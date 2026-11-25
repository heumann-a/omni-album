package com.skript.omnialbum.providers.lychee

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LycheeAlbumResponse(
    val config: LycheeConfig? = null,
    val resource: LycheeAlbumResource
)

@Serializable
data class LycheeConfig(
    @SerialName("is_password_protected") val isPasswordProtected: Boolean? = null
)

@Serializable
data class LycheeAlbumResource(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val photos: List<LycheePhoto>? = null,
    val policy: LycheePolicy? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    val thumb: LycheeThumb? = null,
    @SerialName("preFormattedData") val preFormattedData: LycheePreFormattedData? = null
)

@Serializable
data class LycheePreFormattedData(
    @SerialName("num_photos") val numPhotos: Int? = null
)

@Serializable
data class LycheePhoto(
    val id: String,
    val type: String? = null,
    val title: String? = null,
    @SerialName("taken_at") val takenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("size_variants") val sizeVariants: LycheeSizeVariants? = null,
    val preformatted: LycheePreformatted? = null
)

@Serializable
data class LycheeSizeVariants(
    val thumb: LycheeSizeVariant? = null,
    val thumb2x: LycheeSizeVariant? = null,
    val small: LycheeSizeVariant? = null,
    val small2x: LycheeSizeVariant? = null,
    val medium: LycheeSizeVariant? = null,
    val medium2x: LycheeSizeVariant? = null,
    val original: LycheeSizeVariant? = null
)

@Serializable
data class LycheeSizeVariant(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val filesize: String? = null
)

@Serializable
data class LycheePreformatted(
    val make: String? = null,
    val model: String? = null,
    val shutter: String? = null,
    val aperture: String? = null,
    val iso: String? = null,
    val lens: String? = null,
    val focal: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val location: String? = null
)

@Serializable
data class LycheePolicy(
    @SerialName("is_password_required") val isPasswordRequired: Boolean? = null,
    @SerialName("grants_download") val grantsDownload: Boolean? = null
)

@Serializable
data class LycheeThumb(
    val thumb: String? = null,
    val thumb2x: String? = null
)
