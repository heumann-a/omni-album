package com.skript.omnialbum.model

import kotlinx.serialization.Serializable

@Serializable
data class DisplayAlbum(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String? = null,
    val baseURL: String,
    val shareKey: String,
    val albumId: String,
    val assetCount: Int? = null,
    val allowUpload: Boolean = false,
    val allowDownload: Boolean = true,
    val showMetadata: Boolean = true,
    val password: String? = null,
    val provider: PhotoProvider = PhotoProvider.IMMICH
)
