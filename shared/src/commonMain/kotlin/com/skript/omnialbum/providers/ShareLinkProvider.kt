package com.skript.omnialbum.providers

import com.skript.omnialbum.model.AlbumDetail
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.model.UploadTask
import com.skript.omnialbum.net.UnsupportedApiVersionException

class ProviderError(message: String) : Exception(message) {
    companion object {
        val decryptionNotSupported =
            ProviderError("This provider does not support client-side decryption.")
    }
}

data class ParsedShareLinkResult(
    val baseURL: String,
    val key: String? = null,
    val slug: String? = null,
    val token: String? = null
)

data class SharedLinkInfo(
    val linkId: String,
    val albumId: String,
    val albumName: String,
    val thumbnailAssetId: String?,
    val assetCount: Int,
    val shareKey: String,
    val allowUpload: Boolean,
    val allowDownload: Boolean,
    val showMetadata: Boolean
)

enum class AssetVariant {
    THUMBNAIL,
    PREVIEW,
    ORIGINAL
}

interface ShareLinkProvider {
    val providerType: PhotoProvider

    val requiresClientDecryption: Boolean
        get() = false

    val supportedApiVersions: List<String>
        get() = listOf(currentApiVersion)

    val currentApiVersion: String
        get() = "v1"

    var selectedApiVersion: String
        get() = currentApiVersion
        set(value) {
            if (!supportedApiVersions.contains(value)) {
                throw UnsupportedApiVersionException(value, supportedApiVersions)
            }
        }

    suspend fun detectApiVersion(
        baseURL: String,
        key: String? = null,
        token: String? = null,
        password: String? = null
    ): String = currentApiVersion

    fun canHandle(urlString: String): Boolean

    fun parseShareLink(urlString: String): ParsedShareLinkResult

    suspend fun fetchSharedLinkInfo(
        baseURL: String,
        key: String?,
        slug: String?,
        token: String?,
        password: String?
    ): SharedLinkInfo

    suspend fun fetchAlbumDetail(
        baseURL: String,
        albumId: String,
        shareKey: String,
        password: String?
    ): AlbumDetail

    fun thumbnailURL(baseURL: String, assetId: String, shareKey: String): String?

    fun previewURL(baseURL: String, asset: Asset, shareKey: String): String?

    fun originalURL(baseURL: String, asset: Asset, shareKey: String): String?

    fun videoPlaybackURL(baseURL: String, asset: Asset, shareKey: String): String?

    suspend fun uploadAsset(
        task: UploadTask,
        fileData: ByteArray,
        mimeType: String,
        progressHandler: (Double) -> Unit
    ): String

    suspend fun fetchDecryptedData(
        baseURL: String,
        asset: Asset,
        shareKey: String,
        variant: AssetVariant
    ): ByteArray = throw ProviderError.decryptionNotSupported
}
