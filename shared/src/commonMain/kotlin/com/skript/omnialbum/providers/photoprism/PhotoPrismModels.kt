package com.skript.omnialbum.providers.photoprism

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhotoPrismSessionResponse(
    val id: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    val config: PhotoPrismConfig? = null,
    val status: String? = null
) {
    @Serializable
    data class PhotoPrismConfig(
        val previewToken: String? = null,
        val downloadToken: String? = null,
        val authMode: String? = null,
        @SerialName("public") val public: Boolean? = null
    )
}

@Serializable
data class PhotoPrismPhoto(
    @SerialName("UID") val uid: String,
    @SerialName("Hash") val hash: String,
    @SerialName("Type") val typeSrc: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("TakenAt") val takenAt: String? = null,
    @SerialName("TakenAtLocal") val takenAtLocal: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("CameraMake") val cameraMake: String? = null,
    @SerialName("CameraModel") val cameraModel: String? = null,
    @SerialName("LensModel") val lensModel: String? = null,
    @SerialName("FNumber") val fNumber: Double? = null,
    @SerialName("Iso") val iso: Int? = null,
    @SerialName("FocalLength") val focalLength: Int? = null,
    @SerialName("Exposure") val exposure: String? = null,
    @SerialName("Lat") val lat: Double? = null,
    @SerialName("Lng") val lng: Double? = null,
    @SerialName("Country") val country: String? = null,
    @SerialName("Files") val files: List<PhotoPrismFile>? = null
) {
    val mediaType: String? get() = typeSrc
    val primaryFileHash: String
        get() = files?.firstOrNull { it.primary == true }?.hash ?: hash
}

@Serializable
data class PhotoPrismFile(
    @SerialName("UID") val uid: String? = null,
    @SerialName("Hash") val hash: String,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Primary") val primary: Boolean? = null,
    @SerialName("FileType") val fileType: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Size") val size: Long? = null
)

data class PhotoPrismSession(
    val accessToken: String?,
    val previewToken: String,
    val downloadToken: String,
    val isPublic: Boolean
)

data class PhotoPrismShareInfo(
    val session: PhotoPrismSession,
    val albumUID: String,
    val albumTitle: String?
)
