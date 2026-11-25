package com.skript.omnialbum.model

import kotlinx.serialization.Serializable

@Serializable
data class AlbumSharedUser(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val avatarColor: String? = null
)

@Serializable
data class AlbumDetail(
    val albumName: String,
    val description: String? = null,
    val thumbnailAssetId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val ownerName: String? = null,
    val ownerEmail: String? = null,
    val ownerAvatarColor: String? = null,
    val assetCount: Int,
    val assets: List<Asset>,
    val sharedUsers: List<AlbumSharedUser> = emptyList()
)
