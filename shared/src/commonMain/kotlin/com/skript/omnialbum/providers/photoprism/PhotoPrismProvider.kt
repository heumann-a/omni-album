package com.skript.omnialbum.providers.photoprism

import com.skript.omnialbum.model.AlbumDetail
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.AssetExif
import com.skript.omnialbum.model.AssetType
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.model.UploadTask
import com.skript.omnialbum.net.normalizeBaseURL
import com.skript.omnialbum.providers.ParsedShareLinkResult
import com.skript.omnialbum.providers.ShareLinkProvider
import com.skript.omnialbum.providers.SharedLinkInfo
import io.ktor.http.Url
import kotlinx.datetime.Clock

class PhotoPrismShareLinkException(message: String) : Exception(message) {
    companion object {
        val uploadNotSupported = PhotoPrismShareLinkException("PhotoPrism does not support uploads via share links.")
    }
}

class PhotoPrismProvider : ShareLinkProvider {
    override val providerType: PhotoProvider = PhotoProvider.PHOTOPRISM

    private val service = PhotoPrismService()

    private fun buildCompositeKey(linkToken: String, previewToken: String, downloadToken: String, albumUID: String) =
        "$linkToken|$previewToken|$downloadToken|$albumUID"

    private fun parseCompositeKey(shareKey: String): List<String>? =
        shareKey.split('|').takeIf { it.size == 4 }

    override fun canHandle(urlString: String): Boolean {
        val trimmed = urlString.trim()
        val s = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val url = runCatching { Url(s) }.getOrNull() ?: return false
        val pathComponents = url.encodedPath.split('/').filter { it.isNotEmpty() }
        val sIndex = pathComponents.indexOf("s")
        if (sIndex < 0 || sIndex + 1 >= pathComponents.size) return false
        return !trimmed.contains("/share/") && !trimmed.contains("/apps/photos/")
    }

    override fun parseShareLink(urlString: String): ParsedShareLinkResult {
        var s = urlString.trim()
        if (!s.contains("://")) s = "https://$s"
        val url = runCatching { Url(s) }.getOrNull()
            ?: throw PhotoPrismShareLinkException("Not a valid URL: $urlString")
        if (url.host.isEmpty()) throw PhotoPrismShareLinkException("Not a valid URL: $urlString")
        val pathComponents = url.encodedPath.split('/').filter { it.isNotEmpty() }
        val sIndex = pathComponents.indexOf("s")
        if (sIndex < 0 || sIndex + 1 >= pathComponents.size) {
            throw PhotoPrismShareLinkException("URL must be a PhotoPrism share link (path contains /s/).")
        }
        val token = pathComponents[sIndex + 1]
        if (token.isEmpty()) throw PhotoPrismShareLinkException("Share link must include an album token.")
        val slug = pathComponents.getOrNull(sIndex + 2)
        val scheme = url.protocol.name
        var base = "$scheme://${url.host}"
        val port = url.explicitPort
        if (port != null && !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)) {
            base += ":$port"
        }
        return ParsedShareLinkResult(baseURL = base, key = token, slug = slug, token = token)
    }

    override suspend fun fetchSharedLinkInfo(
        baseURL: String,
        key: String?,
        slug: String?,
        token: String?,
        password: String?
    ): SharedLinkInfo {
        val linkToken = token ?: key
        if (linkToken.isNullOrEmpty()) throw PhotoPrismShareLinkException("Share link must include an album token.")
        val shareInfo = service.resolveShareLink(baseURL, linkToken, slug, password)
        val (photos, returnedCount) = service.searchPhotos(
            baseURL = baseURL,
            albumUID = shareInfo.albumUID,
            accessToken = shareInfo.session.accessToken,
            count = 10000,
            offset = 0
        )
        val photoCount = returnedCount ?: photos.size
        val coverHash = photos.firstOrNull()?.primaryFileHash
        val compositeKey = buildCompositeKey(
            linkToken,
            shareInfo.session.previewToken,
            shareInfo.session.downloadToken,
            shareInfo.albumUID
        )
        val albumName = shareInfo.albumTitle?.takeIf { it.isNotEmpty() }
            ?: slug?.takeIf { it.isNotEmpty() }?.split('-')?.joinToString(" ") { part ->
                part.replaceFirstChar { c -> c.uppercase() }
            }
            ?: "Shared Album"
        return SharedLinkInfo(
            linkId = linkToken,
            albumId = shareInfo.albumUID,
            albumName = albumName,
            thumbnailAssetId = coverHash,
            assetCount = photoCount,
            shareKey = compositeKey,
            allowUpload = false,
            allowDownload = true,
            showMetadata = true
        )
    }

    override suspend fun fetchAlbumDetail(
        baseURL: String,
        albumId: String,
        shareKey: String,
        password: String?
    ): AlbumDetail {
        val keys = parseCompositeKey(shareKey)
            ?: throw PhotoPrismShareLinkException("Share link must include an album token.")
        val base = normalizeBaseURL(baseURL)
            ?: throw PhotoPrismServiceError.invalidBaseURL(baseURL)
        val ppSession = service.establishSession(base, keys[0], password)
        val allPhotos = service.fetchAllPhotos(baseURL, keys[3], ppSession.accessToken)
        val assets = allPhotos.mapNotNull { mapPhotoToAsset(it) }
        val now = Clock.System.now().toString()
        return AlbumDetail(
            albumName = albumId,
            thumbnailAssetId = assets.firstOrNull()?.id,
            createdAt = now,
            updatedAt = now,
            assetCount = assets.size,
            assets = assets,
            sharedUsers = emptyList()
        )
    }

    override fun thumbnailURL(baseURL: String, assetId: String, shareKey: String): String? {
        val keys = parseCompositeKey(shareKey) ?: return null
        return "$baseURL/api/v1/t/$assetId/${keys[1]}/tile_500"
    }

    override fun previewURL(baseURL: String, asset: Asset, shareKey: String): String? {
        val keys = parseCompositeKey(shareKey) ?: return null
        val hash = asset.providerData?.get("fileHash") ?: asset.id
        return "$baseURL/api/v1/t/$hash/${keys[1]}/fit_2048"
    }

    override fun originalURL(baseURL: String, asset: Asset, shareKey: String): String? {
        val keys = parseCompositeKey(shareKey) ?: return null
        val hash = asset.providerData?.get("fileHash") ?: asset.id
        return "$baseURL/api/v1/t/$hash/${keys[1]}/fit_4096"
    }

    override fun videoPlaybackURL(baseURL: String, asset: Asset, shareKey: String): String? {
        if (asset.type != AssetType.VIDEO) return null
        val keys = parseCompositeKey(shareKey) ?: return null
        val hash = asset.providerData?.get("fileHash") ?: asset.id
        return "$baseURL/api/v1/videos/$hash/${keys[1]}/avc"
    }

    override suspend fun uploadAsset(
        task: UploadTask,
        fileData: ByteArray,
        mimeType: String,
        progressHandler: (Double) -> Unit
    ): String = throw PhotoPrismShareLinkException.uploadNotSupported

    private fun mapPhotoToAsset(photo: PhotoPrismPhoto): Asset? {
        val primaryFile = photo.files?.firstOrNull { it.primary == true } ?: photo.files?.firstOrNull()
        val fileHash = primaryFile?.hash ?: photo.hash
        val assetType = if (photo.mediaType?.lowercase() == "video") AssetType.VIDEO else AssetType.IMAGE
        val hasExif = photo.cameraMake != null || photo.cameraModel != null ||
            photo.fNumber != null || photo.iso != null || photo.focalLength != null
        val exif = if (hasExif) {
            AssetExif(
                make = photo.cameraMake,
                model = photo.cameraModel,
                fNumber = photo.fNumber,
                focalLength = photo.focalLength?.toDouble(),
                iso = photo.iso,
                exposureTime = photo.exposure,
                width = primaryFile?.width ?: photo.width,
                height = primaryFile?.height ?: photo.height,
                fileSizeInBytes = primaryFile?.size,
                latitude = photo.lat?.takeIf { it != 0.0 },
                longitude = photo.lng?.takeIf { it != 0.0 },
                country = photo.country?.takeIf { it != "zz" },
                lensModel = photo.lensModel
            )
        } else {
            null
        }
        val providerData = mutableMapOf(
            "uid" to photo.uid,
            "fileHash" to fileHash
        )
        primaryFile?.name?.let { providerData["filename"] = it }
        return Asset(
            id = fileHash,
            type = assetType,
            originalFileName = primaryFile?.name ?: photo.title ?: photo.uid,
            createdAt = photo.takenAtLocal ?: photo.takenAt,
            width = primaryFile?.width ?: photo.width,
            height = primaryFile?.height ?: photo.height,
            exif = exif,
            providerData = providerData
        )
    }
}
