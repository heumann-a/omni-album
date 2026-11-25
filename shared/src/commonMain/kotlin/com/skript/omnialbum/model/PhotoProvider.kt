package com.skript.omnialbum.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PhotoProvider {
    @SerialName("immich") IMMICH,
    @SerialName("nextcloud") NEXTCLOUD,
    @SerialName("photoprism") PHOTOPRISM,
    @SerialName("lychee") LYCHEE,
    @SerialName("ente") ENTE;

    val displayName: String
        get() = when (this) {
            IMMICH -> "Immich"
            NEXTCLOUD -> "Nextcloud"
            PHOTOPRISM -> "PhotoPrism"
            LYCHEE -> "Lychee"
            ENTE -> "Ente"
        }

    val logoAssetName: String?
        get() = when (this) {
            IMMICH -> "ProviderImmich"
            NEXTCLOUD -> "ProviderNextcloud"
            PHOTOPRISM -> "ProviderPhotoPrism"
            ENTE -> "ProviderEnte"
            LYCHEE -> null
        }

    val accentColorHex: String
        get() = when (this) {
            IMMICH -> "#2196F3"
            NEXTCLOUD -> "#00BCD4"
            PHOTOPRISM -> "#FF9800"
            LYCHEE -> "#4CAF50"
            ENTE -> "#9C27B0"
        }

    val tagline: String
        get() = when (this) {
            IMMICH -> "Self-hosted photo & video backup"
            NEXTCLOUD -> "Self-hosted productivity platform"
            PHOTOPRISM -> "AI-powered photo app"
            LYCHEE -> "Self-hosted photo management"
            ENTE -> "End-to-end encrypted photo storage"
        }

    val linkPlaceholderLabel: String
        get() = when (this) {
            IMMICH -> "Immich share link"
            NEXTCLOUD -> "Nextcloud share link"
            PHOTOPRISM -> "PhotoPrism share link"
            LYCHEE -> "Lychee gallery link"
            ENTE -> "Ente album link"
        }

    val linkPlaceholder: String
        get() = when (this) {
            IMMICH -> "https://…/share/…"
            NEXTCLOUD -> "https://…/apps/photos/public/…"
            PHOTOPRISM -> "https://…/s/…/…"
            LYCHEE -> "https://…/gallery/…"
            ENTE -> "https://albums.ente.io/…"
        }

    val linkFooter: String
        get() = when (this) {
            IMMICH -> "Paste the full link you get when sharing an album from Immich."
            NEXTCLOUD -> "Paste the public album link from Nextcloud Photos."
            PHOTOPRISM -> "Paste the share link from PhotoPrism."
            LYCHEE -> "Paste the gallery link for your public Lychee album."
            ENTE -> "Paste the shared album link from Ente Photos."
        }

    val isSupported: Boolean get() = true
}
