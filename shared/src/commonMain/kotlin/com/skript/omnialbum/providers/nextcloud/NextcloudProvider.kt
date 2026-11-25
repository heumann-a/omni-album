package com.skript.omnialbum.providers.nextcloud

import com.skript.omnialbum.model.AlbumDetail
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.AssetExif
import com.skript.omnialbum.model.AssetType
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.model.UploadTask
import com.skript.omnialbum.net.buildUrl
import com.skript.omnialbum.net.encodePathSegment
import com.skript.omnialbum.net.normalizeBaseURL
import com.skript.omnialbum.net.percentDecode
import com.skript.omnialbum.providers.ParsedShareLinkResult
import com.skript.omnialbum.providers.ShareLinkProvider
import com.skript.omnialbum.providers.SharedLinkInfo
import io.ktor.http.Url
import kotlinx.datetime.Clock

class NextcloudShareLinkException(message: String) : Exception(message)

class NextcloudProvider : ShareLinkProvider {
    override val providerType: PhotoProvider = PhotoProvider.NEXTCLOUD

    private val service = NextcloudService()

    override fun canHandle(urlString: String): Boolean =
        urlString.contains("/apps/photos/public/")

    override fun parseShareLink(urlString: String): ParsedShareLinkResult {
        var s = urlString.trim()
        if (!s.contains("://")) s = "https://$s"
        val url = runCatching { Url(s) }.getOrNull()
            ?: throw NextcloudShareLinkException("Not a valid URL: $urlString")
        if (url.host.isEmpty()) throw NextcloudShareLinkException("Not a valid URL: $urlString")
        val path = url.encodedPath
        if (!path.contains("/apps/photos/public/")) {
            throw NextcloudShareLinkException(
                "URL must be a Nextcloud Photos public album link (path contains /apps/photos/public/)."
            )
        }
        val scheme = url.protocol.name
        var base = "$scheme://${url.host}"
        val port = url.explicitPort
        if (port != null && !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)) {
            base += ":$port"
        }
        val pathParts = path.split('/').filter { it.isNotEmpty() }
        val publicIndex = pathParts.indexOf("public")
        if (publicIndex < 0 || publicIndex + 1 >= pathParts.size) {
            throw NextcloudShareLinkException("Share link must include an album token.")
        }
        val token = pathParts[publicIndex + 1]
        if (token.isEmpty()) throw NextcloudShareLinkException("Share link must include an album token.")
        return ParsedShareLinkResult(baseURL = base, key = token, slug = null, token = token)
    }

    override suspend fun fetchSharedLinkInfo(
        baseURL: String,
        key: String?,
        slug: String?,
        token: String?,
        password: String?
    ): SharedLinkInfo {
        val shareToken = token ?: key
        if (shareToken.isNullOrEmpty()) throw NextcloudShareLinkException("Share link must include an album token.")
        val albumInfo = service.fetchAlbumInfo(baseURL, shareToken, password)
        val files = service.listFiles(baseURL, shareToken, password)
        val albumName = albumInfo.originalName ?: albumInfo.displayName ?: "Shared Album"
        val assetCount = albumInfo.nbItems ?: files.size
        val permissions = albumInfo.permissions ?: ""
        val allowUpload = permissions.contains("C") || permissions.contains("N") || permissions.contains("W")
        return SharedLinkInfo(
            linkId = shareToken,
            albumId = shareToken,
            albumName = albumName,
            thumbnailAssetId = files.firstOrNull()?.fileId,
            assetCount = assetCount,
            shareKey = shareToken,
            allowUpload = allowUpload,
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
        val albumInfo = service.fetchAlbumInfo(baseURL, shareKey, password)
        val files = service.listFiles(baseURL, shareKey, password)
        val assets = files.mapNotNull { file ->
            val fileId = file.fileId ?: return@mapNotNull null
            val fileName = file.fileName ?: "unknown"
            val providerData = mutableMapOf<String, String>()
            providerData["filename"] = fileName
            file.contentType?.let { providerData["contentType"] = it }
            val hrefName = file.href.trim('/').split('/').lastOrNull()?.let { percentDecode(it) }
            if (hrefName != null) providerData["hrefName"] = hrefName
            Asset(
                id = fileId,
                type = if (file.isVideo) AssetType.VIDEO else AssetType.IMAGE,
                originalFileName = fileName,
                createdAt = file.originalDateTime ?: file.lastModified,
                width = file.width,
                height = file.height,
                exif = file.contentLength?.let { size ->
                    AssetExif(
                        width = file.width,
                        height = file.height,
                        fileSizeInBytes = size
                    )
                },
                providerData = providerData
            )
        }
        val albumName = albumInfo.originalName ?: albumInfo.displayName ?: "Shared Album"
        val now = Clock.System.now().toString()
        return AlbumDetail(
            albumName = albumName,
            description = albumInfo.location,
            thumbnailAssetId = files.firstOrNull()?.fileId,
            createdAt = now,
            updatedAt = now,
            assetCount = albumInfo.nbItems ?: assets.size,
            assets = assets,
            sharedUsers = emptyList()
        )
    }

    override fun thumbnailURL(baseURL: String, assetId: String, shareKey: String): String =
        buildUrl(
            baseURL,
            "/apps/photos/api/v1/publicPreview/${encodePathSegment(assetId)}",
            listOf("x" to "256", "y" to "256", "token" to shareKey)
        )

    override fun previewURL(baseURL: String, asset: Asset, shareKey: String): String =
        buildUrl(
            baseURL,
            "/apps/photos/api/v1/publicPreview/${encodePathSegment(asset.id)}",
            listOf("x" to "2048", "y" to "2048", "token" to shareKey)
        )

    override fun originalURL(baseURL: String, asset: Asset, shareKey: String): String? {
        val hrefName = asset.providerData?.get("hrefName") ?: asset.providerData?.get("filename") ?: return null
        val base = normalizeBaseURL(baseURL) ?: return null
        val baseUrl = Url(base)
        var authBase = "${baseUrl.protocol.name}://${encodePathSegment(shareKey)}:@${baseUrl.host}"
        baseUrl.explicitPort?.let { authBase += ":$it" }
        return "$authBase/remote.php/dav/photospublic/$shareKey/${encodePathSegment(hrefName)}"
    }

    override fun videoPlaybackURL(baseURL: String, asset: Asset, shareKey: String): String? {
        if (asset.type != AssetType.VIDEO) return null
        return originalURL(baseURL, asset, shareKey)
    }

    override suspend fun uploadAsset(
        task: UploadTask,
        fileData: ByteArray,
        mimeType: String,
        progressHandler: (Double) -> Unit
    ): String {
        service.uploadFile(
            baseURL = task.baseURL,
            token = task.shareKey,
            password = task.password,
            fileName = task.fileName,
            data = fileData,
            progressHandler = progressHandler
        )
        return task.fileName
    }
}
