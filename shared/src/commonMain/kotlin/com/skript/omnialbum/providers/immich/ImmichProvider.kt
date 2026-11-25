package com.skript.omnialbum.providers.immich

import com.skript.omnialbum.model.AlbumDetail
import com.skript.omnialbum.model.AlbumSharedUser
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.AssetExif
import com.skript.omnialbum.model.AssetType
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.model.UploadResponse
import com.skript.omnialbum.model.UploadTask
import com.skript.omnialbum.net.HttpClientHolder
import com.skript.omnialbum.net.buildUrl
import com.skript.omnialbum.net.encodePathSegment
import com.skript.omnialbum.platform.platformDeviceId
import com.skript.omnialbum.providers.AssetVariant
import com.skript.omnialbum.providers.ParsedShareLinkResult
import com.skript.omnialbum.providers.ProviderError
import com.skript.omnialbum.providers.ShareLinkProvider
import com.skript.omnialbum.providers.SharedLinkInfo
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.body
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.MultiPartFormDataContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class ImmichShareLinkParseException(message: String) : Exception(message)

class ImmichProvider : ShareLinkProvider {
    override val providerType: PhotoProvider = PhotoProvider.IMMICH

    private val service = ImmichService(HttpClientHolder.client)

    override fun canHandle(urlString: String): Boolean = urlString.contains("/share/")

    override fun parseShareLink(urlString: String): ParsedShareLinkResult {
        var s = urlString.trim()
        if (!s.contains("://")) s = "https://$s"
        val url = runCatching { io.ktor.http.Url(s) }.getOrNull()
            ?: throw ImmichShareLinkParseException("Not a valid URL: $urlString")
        val host = url.host
        if (host.isEmpty()) throw ImmichShareLinkParseException("Not a valid URL: $urlString")
        val path = url.encodedPath
        if (!path.contains("/share/")) {
            throw ImmichShareLinkParseException("URL must be an Immich share link (path contains /share/).")
        }
        val scheme = url.protocol.name
        var base = "$scheme://$host"
        val port = url.explicitPort
        if (port != null && !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)) {
            base += ":$port"
        }
        val pathParts = path.split('/').filter { it.isNotEmpty() }
        val shareIndex = pathParts.indexOf("share")
        if (shareIndex < 0 || shareIndex + 1 >= pathParts.size) {
            throw ImmichShareLinkParseException("Share link must include a key or token.")
        }
        val afterShare = pathParts[shareIndex + 1]
        val queryKey = url.parameters["key"]
        val key: String
        val slug: String?
        val token: String?
        if (queryKey == null) {
            key = afterShare
            token = afterShare
            slug = null
        } else {
            key = queryKey
            slug = afterShare
            token = null
        }
        if (key.isEmpty()) throw ImmichShareLinkParseException("Share link must include a key or token.")
        return ParsedShareLinkResult(
            baseURL = base,
            key = key,
            slug = slug?.takeIf { it.isNotEmpty() },
            token = token
        )
    }

    override suspend fun fetchSharedLinkInfo(
        baseURL: String,
        key: String?,
        slug: String?,
        token: String?,
        password: String?
    ): SharedLinkInfo {
        val response = service.fetchSharedLink(baseURL, key, slug, token, password)
        val album = response.album ?: throw ImmichServiceError("Invalid response from server")
        return SharedLinkInfo(
            linkId = response.id,
            albumId = album.id,
            albumName = album.albumName,
            thumbnailAssetId = album.albumThumbnailAssetId,
            assetCount = album.assetCount,
            shareKey = response.key,
            allowUpload = response.allowUpload,
            allowDownload = response.allowDownload,
            showMetadata = response.showMetadata
        )
    }

    override suspend fun fetchAlbumDetail(
        baseURL: String,
        albumId: String,
        shareKey: String,
        password: String?
    ): AlbumDetail {
        val response = service.fetchAlbumWithAssets(baseURL, albumId, shareKey, password)
        return AlbumDetail(
            albumName = response.albumName,
            description = response.description,
            thumbnailAssetId = response.albumThumbnailAssetId,
            createdAt = response.createdAt,
            updatedAt = response.updatedAt,
            ownerName = response.owner.name,
            ownerEmail = response.owner.email,
            ownerAvatarColor = response.owner.avatarColor,
            assetCount = response.assetCount,
            assets = response.assets.map { mapAsset(it) },
            sharedUsers = response.albumUsers.mapNotNull { au ->
                au.user?.let {
                    AlbumSharedUser(
                        id = it.id,
                        name = it.name,
                        email = it.email,
                        role = au.role,
                        avatarColor = it.avatarColor
                    )
                }
            }
        )
    }

    override fun thumbnailURL(baseURL: String, assetId: String, shareKey: String): String =
        buildUrl(baseURL, "/api/assets/${encodePathSegment(assetId)}/thumbnail", listOf("key" to shareKey))

    override fun previewURL(baseURL: String, asset: Asset, shareKey: String): String =
        buildUrl(
            baseURL,
            "/api/assets/${encodePathSegment(asset.id)}/thumbnail",
            listOf("key" to shareKey, "size" to "preview", "edited" to "true")
        )

    override fun originalURL(baseURL: String, asset: Asset, shareKey: String): String =
        buildUrl(baseURL, "/api/assets/${encodePathSegment(asset.id)}/original", listOf("key" to shareKey))

    override fun videoPlaybackURL(baseURL: String, asset: Asset, shareKey: String): String? {
        if (asset.type != AssetType.VIDEO) return null
        val query = mutableListOf("key" to shareKey)
        asset.providerData?.get("checksum")?.let { query.add("c" to it) }
        return buildUrl(baseURL, "/api/assets/${encodePathSegment(asset.id)}/video/playback", query)
    }

    override suspend fun uploadAsset(
        task: UploadTask,
        fileData: ByteArray,
        mimeType: String,
        progressHandler: (Double) -> Unit
    ): String {
        val client = HttpClientHolder.client
        val query = mutableListOf("key" to task.shareKey)
        if (!task.password.isNullOrEmpty()) query.add("password" to task.password)
        val response: HttpResponse = client.preparePost {
            url(buildUrl(task.baseURL, "/api/assets", query))
            val boundary = "boundary-${task.id}"
            headers {
                append(HttpHeaders.ContentType, "multipart/form-data; boundary=$boundary")
            }
            setBody(buildMultipartBody(boundary, fileData, task, mimeType))
            onUpload { bytesSentTotal, contentLength ->
                if (contentLength > 0) progressHandler(minOf(bytesSentTotal.toDouble() / contentLength, 0.99))
            }
        }.execute()
        if (response.status.isSuccess()) {
            val uploadResponse: UploadResponse = response.body()
            addAssetToAlbum(client, task, uploadResponse.id)
            return uploadResponse.id
        }
        val errorMessage = runCatching { response.bodyAsText() }.getOrNull() ?: "Unknown error"
        throw ImmichServiceError("Upload failed. Status ${response.status.value}: $errorMessage")
    }

    private suspend fun addAssetToAlbum(client: io.ktor.client.HttpClient, task: UploadTask, assetId: String) {
        val query = mutableListOf("key" to task.shareKey)
        if (!task.password.isNullOrEmpty()) query.add("password" to task.password)
        val response = client.post {
            url(buildUrl(task.baseURL, "/api/albums/${encodePathSegment(task.albumId)}/assets", query))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(Json.encodeToString(mapOf("ids" to listOf(assetId))))
        }
        if (!response.status.isSuccess()) {
            // Upload itself succeeded; adding to album is best-effort, same as the iOS app
        }
    }

    private fun buildMultipartBody(
        boundary: String,
        fileData: ByteArray,
        task: UploadTask,
        mimeType: String
    ): MultiPartFormDataContent {
        val isoCreated = task.createdAt.toString()
        return MultiPartFormDataContent(
            formData {
                append("deviceAssetId", task.localAssetIdentifier)
                append("deviceId", platformDeviceId())
                append("fileCreatedAt", isoCreated)
                append("fileModifiedAt", isoCreated)
                append("isFavorite", "false")
                append("assetData", fileData, headers {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"assetData\"; filename=\"${task.fileName}\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
            }
        )
    }

    private fun mapAsset(immichAsset: ImmichAsset): Asset {
        val assetType = if (immichAsset.type.lowercase() == "video") AssetType.VIDEO else AssetType.IMAGE
        val exif = immichAsset.exifInfo?.let { e ->
            AssetExif(
                make = e.make,
                model = e.model,
                fNumber = e.fNumber,
                focalLength = e.focalLength,
                iso = e.iso,
                exposureTime = e.exposureTime,
                width = e.exifImageWidth,
                height = e.exifImageHeight,
                fileSizeInBytes = e.fileSizeInByte,
                latitude = e.latitude,
                longitude = e.longitude,
                city = e.city,
                state = e.state,
                country = e.country,
                lensModel = e.lensModel,
                orientation = e.orientation,
                description = e.description
            )
        }
        val providerData = mutableMapOf<String, String>()
        immichAsset.checksum?.let { providerData["checksum"] = it }
        immichAsset.originalMimeType?.let { providerData["originalMimeType"] = it }
        return Asset(
            id = immichAsset.id,
            type = assetType,
            originalFileName = immichAsset.originalFileName,
            createdAt = immichAsset.fileCreatedAt ?: immichAsset.localDateTime ?: immichAsset.createdAt,
            duration = immichAsset.duration,
            thumbhash = immichAsset.thumbhash,
            width = immichAsset.width,
            height = immichAsset.height,
            exif = exif,
            providerData = providerData.ifEmpty { null }
        )
    }
}
