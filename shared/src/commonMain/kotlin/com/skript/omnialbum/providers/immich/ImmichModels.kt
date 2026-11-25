package com.skript.omnialbum.providers.immich

import kotlinx.serialization.Serializable

@Serializable
data class SharedLinkResponse(
    val id: String,
    val key: String,
    val allowUpload: Boolean,
    val allowDownload: Boolean,
    val showMetadata: Boolean,
    val album: SharedLinkAlbum? = null
)

@Serializable
data class SharedLinkAlbum(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int
)

@Serializable
data class AlbumWithAssetsResponse(
    val albumName: String,
    val description: String? = null,
    val albumThumbnailAssetId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val owner: AlbumOwner,
    val albumUsers: List<AlbumUser> = emptyList(),
    val assets: List<ImmichAsset> = emptyList(),
    val assetCount: Int
)

@Serializable
data class AlbumOwner(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val avatarColor: String? = null
)

@Serializable
data class AlbumUser(
    val role: String? = null,
    val user: AlbumOwner? = null
)

@Serializable
data class ImmichAsset(
    val id: String,
    val type: String,
    val originalFileName: String,
    val originalMimeType: String? = null,
    val checksum: String? = null,
    val duration: String? = null,
    val thumbhash: String? = null,
    val fileCreatedAt: String? = null,
    val localDateTime: String? = null,
    val createdAt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val exifInfo: ExifInfo? = null
)

@Serializable
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val exifImageWidth: Int? = null,
    val exifImageHeight: Int? = null,
    val fileSizeInByte: Long? = null,
    val orientation: String? = null,
    val lensModel: String? = null,
    val fNumber: Double? = null,
    val focalLength: Double? = null,
    val iso: Int? = null,
    val exposureTime: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val description: String? = null
)
