package com.skript.omnialbum.providers.nextcloud

import com.skript.omnialbum.net.xmlUnescape

data class NextcloudFile(
    val href: String,
    val displayName: String? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val lastModified: String? = null,
    val fileId: String? = null,
    val permissions: String? = null,
    val isCollection: Boolean = false,
    val hasPreview: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val originalDateTime: String? = null,
    val originalName: String? = null,
    val nbItems: Int? = null,
    val dateRange: String? = null,
    val location: String? = null
) {
    val fileName: String?
        get() {
            val path = href.trim('/')
            val lastComponent = path.split('/').lastOrNull() ?: return null
            val name = com.skript.omnialbum.net.percentDecode(lastComponent)
            val dashIndex = name.indexOf('-')
            return if (dashIndex >= 0) name.substring(dashIndex + 1) else name
        }

    val isMedia: Boolean
        get() {
            val ct = contentType?.lowercase() ?: return false
            return ct.startsWith("image/") || ct.startsWith("video/")
        }

    val isVideo: Boolean
        get() = contentType?.lowercase()?.startsWith("video/") == true
}

internal fun unescape(value: String?): String? = value?.let { xmlUnescape(it) }
