@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.skript.omnialbum.model

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantIso8601", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

@Serializable
enum class UploadStatus {
    @SerialName("pending") PENDING,
    @SerialName("uploading") UPLOADING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED
}

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class UploadTask(
    val id: Uuid = Uuid.random(),
    val albumId: String,
    val albumName: String,
    val baseURL: String,
    val shareKey: String,
    val password: String? = null,
    val localAssetIdentifier: String,
    val fileName: String,
    val fileSize: Long? = null,
    val provider: String = "immich",
    @Serializable(with = InstantIso8601Serializer::class)
    val createdAt: Instant = Instant.fromEpochMilliseconds(0),
    @Serializable(with = InstantIso8601Serializer::class)
    val completedAt: Instant? = null,
    val status: UploadStatus = UploadStatus.PENDING,
    val progress: Double = 0.0,
    val errorMessage: String? = null,
    val uploadedAssetId: String? = null
)

@Serializable
data class UploadResponse(
    val id: String,
    val status: String? = null,
    val duplicate: Boolean? = null,
    val deviceAssetId: String? = null,
    val deviceId: String? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val ownerId: String? = null,
    val type: String? = null,
    val originalFileName: String? = null
)
