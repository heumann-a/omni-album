@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.skript.omnialbum.providers.ente

import com.skript.omnialbum.model.AlbumDetail
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.AssetExif
import com.skript.omnialbum.model.AssetType
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.model.UploadTask
import com.skript.omnialbum.net.ApiVersion
import com.skript.omnialbum.net.ApiVersionCatalog
import com.skript.omnialbum.platform.createParentDirectories
import com.skript.omnialbum.platform.fileExists
import com.skript.omnialbum.platform.platformCacheDir
import com.skript.omnialbum.platform.readFileBytes
import com.skript.omnialbum.platform.writeFileBytes
import com.skript.omnialbum.providers.AssetVariant
import com.skript.omnialbum.providers.ParsedShareLinkResult
import com.skript.omnialbum.providers.ShareLinkProvider
import com.skript.omnialbum.providers.SharedLinkInfo
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json

enum class EnteApiVersion(
    override val id: String,
    override val isCurrent: Boolean,
    override val pathPrefix: String
) : ApiVersion {
    V1("v1", true, "");

    companion object {
        val catalog = ApiVersionCatalog(values().toList())
    }
}

class EnteProvider : ShareLinkProvider {
    override val providerType: PhotoProvider = PhotoProvider.ENTE
    override val requiresClientDecryption: Boolean = true

    private var selected = EnteApiVersion.catalog.current

    override val supportedApiVersions: List<String>
        get() = EnteApiVersion.catalog.supportedIds

    override val currentApiVersion: String
        get() = EnteApiVersion.catalog.current.id

    override var selectedApiVersion: String
        get() = selected.id
        set(value) {
            selected = EnteApiVersion.catalog.requireSupported(value)
        }

    override fun canHandle(urlString: String): Boolean {
        val url = runCatching { Url(urlString) }.getOrNull() ?: return false
        val host = url.host.lowercase()
        return host == "albums.ente.io" || host.endsWith(".ente.io")
    }

    override fun parseShareLink(urlString: String): ParsedShareLinkResult {
        val url = runCatching { Url(urlString) }.getOrNull()
            ?: throw EnteServiceError.invalidBaseURL
        val accessToken = url.parameters["t"]
        if (accessToken.isNullOrEmpty()) throw EnteServiceError.invalidBaseURL
        val fragment = url.fragment
        if (fragment.isEmpty()) throw EnteServiceError.invalidBaseURL
        return ParsedShareLinkResult(
            baseURL = "https://api.ente.io",
            key = fragment,
            slug = null,
            token = accessToken
        )
    }

    override suspend fun fetchSharedLinkInfo(
        baseURL: String,
        key: String?,
        slug: String?,
        token: String?,
        password: String?
    ): SharedLinkInfo {
        val accessToken = token ?: throw EnteServiceError.invalidBaseURL
        val base58Key = key ?: throw EnteServiceError.invalidBaseURL
        val collectionKey = EnteCrypto.decodeBase58(base58Key)
            ?: throw EnteServiceError.decryptionFailed
        val collection = EnteService.fetchCollectionInfo(accessToken)
        val albumName = decryptCollectionName(collection, collectionKey) ?: "Ente Album"
        val files = EnteService.fetchFiles(accessToken)
        val activeFiles = files.filter { it.isDeleted != true }
        val coverFileId = activeFiles.firstOrNull()?.id?.toString()
        activeFiles.firstOrNull()?.let { firstFile ->
            decryptFileKey(firstFile, collectionKey)?.let { fileKey ->
                runCatching {
                    downloadAndCacheThumbnail(firstFile, fileKey, accessToken, accessToken)
                }
            }
        }
        return SharedLinkInfo(
            linkId = collection.id.toString(),
            albumId = collection.id.toString(),
            albumName = albumName,
            thumbnailAssetId = coverFileId,
            assetCount = activeFiles.size,
            shareKey = "$accessToken|$base58Key",
            allowUpload = false,
            allowDownload = collection.publicURLs?.firstOrNull()?.enableDownload ?: true,
            showMetadata = true
        )
    }

    override suspend fun fetchAlbumDetail(
        baseURL: String,
        albumId: String,
        shareKey: String,
        password: String?
    ): AlbumDetail {
        val (accessToken, collectionKey) = parseCompositeShareKey(shareKey)
        val collection = EnteService.fetchCollectionInfo(accessToken)
        val albumName = decryptCollectionName(collection, collectionKey) ?: "Ente Album"
        val files = EnteService.fetchFiles(accessToken)
        val activeFiles = files.filter { it.isDeleted != true }

        val assets = mutableListOf<Asset>()
        val thumbnailWork = mutableListOf<Pair<EnteFile, ByteArray>>()
        for (file in activeFiles) {
            val fileKey = decryptFileKey(file, collectionKey) ?: continue
            val metadata = decryptFileMetadata(file, fileKey)
            val fileName = metadata?.title ?: "IMG_${file.id}.jpg"
            val assetType = guessAssetType(fileName, metadata?.fileType)
            val createdAt = metadata?.creationTime?.let { micros ->
                Instant.fromEpochMilliseconds(micros / 1000).toString()
            }
            val providerData = mapOf(
                "fileId" to file.id.toString(),
                "fileKey" to Base64.encode(fileKey),
                "fileDecryptionHeader" to file.file.decryptionHeader,
                "thumbDecryptionHeader" to file.thumbnail.decryptionHeader
            )
            val exif = if (metadata?.latitude != null || metadata?.longitude != null || metadata?.w != null) {
                AssetExif(
                    width = metadata.w,
                    height = metadata.h,
                    fileSizeInBytes = file.info?.fileSize,
                    latitude = metadata.latitude,
                    longitude = metadata.longitude
                )
            } else if (file.info?.fileSize != null) {
                AssetExif(fileSizeInBytes = file.info.fileSize)
            } else {
                null
            }
            assets.add(
                Asset(
                    id = file.id.toString(),
                    type = assetType,
                    originalFileName = fileName,
                    createdAt = createdAt,
                    width = metadata?.w,
                    height = metadata?.h,
                    exif = exif,
                    providerData = providerData
                )
            )
            thumbnailWork.add(file to fileKey)
        }

        coroutineScope {
            thumbnailWork.map { (file, fileKey) ->
                async {
                    runCatching { downloadAndCacheThumbnail(file, fileKey, accessToken, accessToken) }
                }
            }.awaitAll()
        }

        val now = Clock.System.now().toString()
        return AlbumDetail(
            albumName = albumName,
            thumbnailAssetId = activeFiles.firstOrNull()?.id?.toString(),
            createdAt = now,
            updatedAt = now,
            assetCount = assets.size,
            assets = assets,
            sharedUsers = emptyList()
        )
    }

    override fun thumbnailURL(baseURL: String, assetId: String, shareKey: String): String? {
        val cacheDir = extractCacheDir(shareKey)
        val path = thumbnailCachePath(cacheDir, assetId)
        return if (fileExists(path)) "file://$path" else null
    }

    override fun previewURL(baseURL: String, asset: Asset, shareKey: String): String? = null

    override fun originalURL(baseURL: String, asset: Asset, shareKey: String): String? = null

    override fun videoPlaybackURL(baseURL: String, asset: Asset, shareKey: String): String? = null

    override suspend fun uploadAsset(
        task: UploadTask,
        fileData: ByteArray,
        mimeType: String,
        progressHandler: (Double) -> Unit
    ): String = throw EnteServiceError.invalidResponse

    override suspend fun fetchDecryptedData(
        baseURL: String,
        asset: Asset,
        shareKey: String,
        variant: AssetVariant
    ): ByteArray {
        val (accessToken, _) = parseCompositeShareKey(shareKey)
        val fileIdStr = asset.providerData?.get("fileId")
            ?: throw EnteServiceError.decryptionFailed
        val fileId = fileIdStr.toLongOrNull() ?: throw EnteServiceError.decryptionFailed
        val fileKey = asset.providerData?.get("fileKey")?.let {
            runCatching { Base64.decode(it) }.getOrNull()
        } ?: throw EnteServiceError.decryptionFailed

        return when (variant) {
            AssetVariant.THUMBNAIL -> {
                val header = asset.providerData.getValue("thumbDecryptionHeader").let {
                    runCatching { Base64.decode(it) }.getOrNull()
                } ?: throw EnteServiceError.decryptionFailed
                val encrypted = EnteService.downloadThumbnail(fileId, accessToken)
                EnteCrypto.decryptStream(encrypted, header, fileKey)
                    ?: throw EnteServiceError.decryptionFailed
            }
            AssetVariant.PREVIEW, AssetVariant.ORIGINAL -> {
                val cacheDir = extractCacheDir(shareKey)
                val cachePath = previewCachePath(cacheDir, fileIdStr)
                if (fileExists(cachePath)) {
                    return readFileBytes(cachePath)
                }
                val header = asset.providerData.getValue("fileDecryptionHeader").let {
                    runCatching { Base64.decode(it) }.getOrNull()
                } ?: throw EnteServiceError.decryptionFailed
                val encrypted = EnteService.downloadFile(fileId, accessToken)
                val decrypted = EnteCrypto.decryptStream(encrypted, header, fileKey)
                    ?: throw EnteServiceError.decryptionFailed
                runCatching {
                    createParentDirectories(cachePath)
                    writeFileBytes(cachePath, decrypted)
                }
                decrypted
            }
        }
    }

    private fun parseCompositeShareKey(shareKey: String): Pair<String, ByteArray> {
        val parts = shareKey.split('|', limit = 2)
        if (parts.size != 2) throw EnteServiceError.invalidBaseURL
        val collectionKey = EnteCrypto.decodeBase58(parts[1])
            ?: throw EnteServiceError.decryptionFailed
        return parts[0] to collectionKey
    }

    private fun extractCacheDir(shareKey: String): String =
        shareKey.split('|', limit = 2).firstOrNull() ?: "unknown"

    private fun decryptCollectionName(collection: EnteCollection, collectionKey: ByteArray): String? {
        val encryptedName = collection.encryptedName ?: return null
        val nonceStr = collection.nameDecryptionNonce ?: return null
        val encrypted = runCatching { Base64.decode(encryptedName) }.getOrNull() ?: return null
        val nonce = runCatching { Base64.decode(nonceStr) }.getOrNull() ?: return null
        val decrypted = EnteCrypto.decryptSecretBox(encrypted, nonce, collectionKey) ?: return null
        return decrypted.decodeToString()
    }

    private fun decryptFileKey(file: EnteFile, collectionKey: ByteArray): ByteArray? {
        val encryptedKey = runCatching { Base64.decode(file.encryptedKey) }.getOrNull() ?: return null
        val nonce = runCatching { Base64.decode(file.keyDecryptionNonce) }.getOrNull() ?: return null
        return EnteCrypto.decryptSecretBox(encryptedKey, nonce, collectionKey)
    }

    private fun decryptFileMetadata(file: EnteFile, fileKey: ByteArray): EnteDecryptedMetadata? {
        val encrypted = runCatching { Base64.decode(file.metadata.encryptedData) }.getOrNull() ?: return null
        val header = runCatching { Base64.decode(file.metadata.decryptionHeader) }.getOrNull() ?: return null
        val decrypted = EnteCrypto.decryptStream(encrypted, header, fileKey) ?: return null
        return runCatching { Json.decodeFromString<EnteDecryptedMetadata>(decrypted.decodeToString()) }.getOrNull()
    }

    private suspend fun downloadAndCacheThumbnail(
        file: EnteFile,
        fileKey: ByteArray,
        accessToken: String,
        cacheDir: String
    ) {
        val cachePath = thumbnailCachePath(cacheDir, file.id.toString())
        if (fileExists(cachePath)) return
        val header = runCatching { Base64.decode(file.thumbnail.decryptionHeader) }.getOrNull() ?: return
        val encrypted = EnteService.downloadThumbnail(file.id, accessToken)
        val decrypted = EnteCrypto.decryptStream(encrypted, header, fileKey) ?: return
        runCatching {
            createParentDirectories(cachePath)
            writeFileBytes(cachePath, decrypted)
        }
    }

    private fun previewCachePath(cacheDir: String, fileId: String): String =
        "${platformCacheDir()}/ente-previews/$cacheDir/$fileId"

    private fun thumbnailCachePath(cacheDir: String, fileId: String): String =
        "${platformCacheDir()}/ente-thumbs/$cacheDir/$fileId.jpg"

    private fun guessAssetType(fileName: String, fileType: Int?): AssetType {
        if (fileType != null) return if (fileType == 1) AssetType.VIDEO else AssetType.IMAGE
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val videoExtensions = listOf("mp4", "mov", "avi", "mkv", "webm", "m4v")
        return if (ext in videoExtensions) AssetType.VIDEO else AssetType.IMAGE
    }
}
