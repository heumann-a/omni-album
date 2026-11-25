package com.skript.omnialbum.providers.lychee

import com.skript.omnialbum.model.AlbumDetail
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.AssetExif
import com.skript.omnialbum.model.AssetType
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.model.UploadTask
import com.skript.omnialbum.providers.ParsedShareLinkResult
import com.skript.omnialbum.providers.ShareLinkProvider
import com.skript.omnialbum.providers.SharedLinkInfo
import io.ktor.http.Url
import kotlinx.datetime.Clock

class LycheeProvider : ShareLinkProvider {
    override val providerType: PhotoProvider = PhotoProvider.LYCHEE

    private val service = LycheeService()

    override fun canHandle(urlString: String): Boolean {
        val url = runCatching { Url(urlString) }.getOrNull() ?: return false
        return url.encodedPath.contains("/gallery/")
    }

    override fun parseShareLink(urlString: String): ParsedShareLinkResult {
        var s = urlString.trim()
        if (!s.contains("://")) s = "https://$s"
        val url = runCatching { Url(s) }.getOrNull()
            ?: throw LycheeServiceError.invalidBaseURL
        val pathComponents = url.encodedPath.split('/').filter { it.isNotEmpty() }
        val galleryIndex = pathComponents.indexOf("gallery")
        if (galleryIndex < 0 || galleryIndex + 1 >= pathComponents.size) throw LycheeServiceError.albumNotFound
        val albumId = pathComponents[galleryIndex + 1]
        if (albumId.isEmpty()) throw LycheeServiceError.albumNotFound
        val scheme = url.protocol.name
        var base = "$scheme://${url.host}"
        val port = url.explicitPort
        if (port != null && !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)) {
            base += ":$port"
        }
        return ParsedShareLinkResult(baseURL = base, key = albumId, slug = null, token = null)
    }

    override suspend fun fetchSharedLinkInfo(
        baseURL: String,
        key: String?,
        slug: String?,
        token: String?,
        password: String?
    ): SharedLinkInfo {
        val albumId = key?.takeIf { it.isNotEmpty() } ?: throw LycheeServiceError.albumNotFound
        service.initSession(baseURL, albumId)
        if (!password.isNullOrEmpty()) service.unlockAlbum(baseURL, albumId, password)
        val albumResponse = service.fetchAlbum(baseURL, albumId)
        val resource = albumResponse.resource
        val isPasswordProtected = albumResponse.config?.isPasswordProtected == true ||
            resource.policy?.isPasswordRequired == true
        if (isPasswordProtected && resource.photos.isNullOrEmpty() && password.isNullOrEmpty()) {
            throw LycheeServiceError.passwordRequired
        }
        val coverThumbId = resolveURL(resource.thumb?.thumb2x ?: resource.thumb?.thumb, baseURL)
        return SharedLinkInfo(
            linkId = albumId,
            albumId = albumId,
            albumName = resource.title ?: "Lychee Album",
            thumbnailAssetId = coverThumbId,
            assetCount = resource.preFormattedData?.numPhotos ?: resource.photos?.size ?: 0,
            shareKey = albumId,
            allowUpload = false,
            allowDownload = resource.policy?.grantsDownload ?: true,
            showMetadata = true
        )
    }

    override suspend fun fetchAlbumDetail(
        baseURL: String,
        albumId: String,
        shareKey: String,
        password: String?
    ): AlbumDetail {
        service.initSession(baseURL, shareKey)
        if (!password.isNullOrEmpty()) service.unlockAlbum(baseURL, shareKey, password)
        val albumResponse = service.fetchAlbum(baseURL, shareKey)
        val resource = albumResponse.resource
        val isPasswordProtected = albumResponse.config?.isPasswordProtected == true ||
            resource.policy?.isPasswordRequired == true
        if (isPasswordProtected && resource.photos.isNullOrEmpty() && password.isNullOrEmpty()) {
            throw LycheeServiceError.passwordRequired
        }
        val assets = resource.photos.orEmpty().mapNotNull { mapPhoto(it, baseURL) }
        val now = Clock.System.now().toString()
        return AlbumDetail(
            albumName = resource.title ?: "Lychee Album",
            description = resource.description,
            thumbnailAssetId = assets.firstOrNull()?.id,
            createdAt = now,
            updatedAt = now,
            ownerName = resource.ownerName,
            assetCount = assets.size,
            assets = assets,
            sharedUsers = emptyList()
        )
    }

    override fun thumbnailURL(baseURL: String, assetId: String, shareKey: String): String = assetId

    override fun previewURL(baseURL: String, asset: Asset, shareKey: String): String? =
        asset.providerData?.get("mediumURL")
            ?: asset.providerData?.get("originalURL")
            ?: asset.id

    override fun originalURL(baseURL: String, asset: Asset, shareKey: String): String? =
        asset.providerData?.get("originalURL")

    override fun videoPlaybackURL(baseURL: String, asset: Asset, shareKey: String): String? {
        if (asset.type != AssetType.VIDEO) return null
        return asset.providerData?.get("originalURL")
    }

    override suspend fun uploadAsset(
        task: UploadTask,
        fileData: ByteArray,
        mimeType: String,
        progressHandler: (Double) -> Unit
    ): String = throw LycheeServiceError.uploadNotSupported

    private fun mapPhoto(photo: LycheePhoto, baseURL: String): Asset? {
        val variants = photo.sizeVariants
        val thumbURL = bestURL(
            listOf(variants?.thumb2x?.url, variants?.thumb?.url, variants?.small2x?.url, variants?.small?.url),
            baseURL
        ) ?: return null
        val assetType = if (photo.type?.lowercase()?.startsWith("video/") == true) AssetType.VIDEO else AssetType.IMAGE
        val providerData = mutableMapOf(
            "lycheeId" to photo.id,
            "thumbURL" to thumbURL
        )
        bestURL(listOf(variants?.medium2x?.url, variants?.medium?.url), baseURL)?.let {
            providerData["mediumURL"] = it
        }
        resolveOptionalURL(variants?.original?.url, baseURL)?.let {
            providerData["originalURL"] = it
        }
        return Asset(
            id = thumbURL,
            type = assetType,
            originalFileName = photo.title ?: "photo_${photo.id}",
            createdAt = photo.takenAt ?: photo.createdAt,
            width = variants?.original?.width,
            height = variants?.original?.height,
            exif = parseExif(photo.preformatted, variants?.original),
            providerData = providerData
        )
    }

    private fun resolveURL(urlString: String?, baseURL: String): String? {
        if (urlString.isNullOrEmpty()) return null
        if (urlString.startsWith("http://") || urlString.startsWith("https://")) return urlString
        val separator = if (urlString.startsWith("/")) "" else "/"
        return "$baseURL$separator$urlString"
    }

    private fun resolveOptionalURL(urlString: String?, baseURL: String): String? =
        resolveURL(urlString, baseURL)

    private fun bestURL(candidates: List<String?>, baseURL: String): String? =
        candidates.firstNotNullOfOrNull { resolveOptionalURL(it, baseURL) }

    private fun parseFileSizeToBytes(str: String?): Long? {
        if (str.isNullOrEmpty()) return null
        val parts = str.trim().split(' ')
        if (parts.size != 2) return null
        val value = parts[0].toDoubleOrNull() ?: return null
        return when (parts[1].uppercase()) {
            "B" -> value.toLong()
            "KB" -> (value * 1024).toLong()
            "MB" -> (value * 1024 * 1024).toLong()
            "GB" -> (value * 1024 * 1024 * 1024).toLong()
            else -> null
        }
    }

    private fun parseExif(pf: LycheePreformatted?, originalVariant: LycheeSizeVariant?): AssetExif? {
        if (pf == null) return null
        val fNumber = pf.aperture?.replace("f/", "")?.replace("ƒ/", "")?.trim()?.toDoubleOrNull()
        val iso = pf.iso?.replace("ISO ", "")?.replace("ISO", "")?.trim()?.toIntOrNull()
        val focalLength = pf.focal?.replace(" mm", "")?.replace("mm", "")?.trim()?.toDoubleOrNull()
        val latitude = pf.latitude?.toDoubleOrNull()
        val longitude = pf.longitude?.toDoubleOrNull()
        val fileSizeInBytes = parseFileSizeToBytes(originalVariant?.filesize)
        val hasData = pf.make != null || pf.model != null || fNumber != null || iso != null ||
            focalLength != null || pf.shutter != null || latitude != null || longitude != null ||
            fileSizeInBytes != null
        if (!hasData) return null
        return AssetExif(
            make = pf.make,
            model = pf.model,
            fNumber = fNumber,
            focalLength = focalLength,
            iso = iso,
            exposureTime = pf.shutter,
            width = originalVariant?.width,
            height = originalVariant?.height,
            fileSizeInBytes = fileSizeInBytes,
            latitude = latitude,
            longitude = longitude,
            lensModel = pf.lens,
            description = pf.location
        )
    }
}
