package com.skript.omnialbum.providers.ente

import kotlinx.serialization.Serializable

@Serializable
data class EnteCollectionResponse(val collection: EnteCollection)

@Serializable
data class EnteCollection(
    val id: Long,
    val encryptedKey: String,
    val keyDecryptionNonce: String,
    val encryptedName: String? = null,
    val nameDecryptionNonce: String? = null,
    val publicURLs: List<EntePublicURL>? = null
)

@Serializable
data class EntePublicURL(
    val enableDownload: Boolean? = null
)

@Serializable
data class EnteDiffResponse(
    val diff: List<EnteFile>,
    val hasMore: Boolean
)

@Serializable
data class EnteFile(
    val id: Long,
    val collectionID: Long? = null,
    val encryptedKey: String,
    val keyDecryptionNonce: String,
    val file: EnteFileData,
    val thumbnail: EnteFileData,
    val metadata: EnteFileMetadata,
    val info: EnteFileInfo? = null,
    val updationTime: Long? = null,
    val isDeleted: Boolean? = null
)

@Serializable
data class EnteFileData(
    val decryptionHeader: String,
    val objectKey: String? = null,
    val size: Long? = null
)

@Serializable
data class EnteFileMetadata(
    val encryptedData: String,
    val decryptionHeader: String
)

@Serializable
data class EnteFileInfo(
    val fileSize: Long? = null,
    val thumbSize: Long? = null
)

@Serializable
data class EnteDecryptedMetadata(
    val title: String? = null,
    val creationTime: Long? = null,
    val modificationTime: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fileType: Int? = null,
    val w: Int? = null,
    val h: Int? = null
)
