package com.skript.omnialbum.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AssetType {
    @SerialName("image") IMAGE,
    @SerialName("video") VIDEO
}

@Serializable
data class AssetExif(
    val make: String? = null,
    val model: String? = null,
    val fNumber: Double? = null,
    val focalLength: Double? = null,
    val iso: Int? = null,
    val exposureTime: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fileSizeInBytes: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val lensModel: String? = null,
    val orientation: String? = null,
    val description: String? = null
)

@Serializable
data class Asset(
    val id: String,
    val type: AssetType,
    val originalFileName: String,
    val createdAt: String? = null,
    val duration: String? = null,
    val thumbhash: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val exif: AssetExif? = null,
    val providerData: Map<String, String>? = null
)
