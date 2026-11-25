package com.skript.omnialbum.providers.nextcloud

import com.skript.omnialbum.net.sharedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object WebDavParser {

    private val responseRegex =
        Regex("<[^>]*:?response[ >].*?</[^>]*:?response\\s*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val collectionRegex =
        Regex("<[^>]*:?collection[^>]*/?>", RegexOption.IGNORE_CASE)

    fun parse(xml: String): List<NextcloudFile> {
        val files = mutableListOf<NextcloudFile>()
        for (match in responseRegex.findAll(xml)) {
            val block = match.value
            val isCollection = collectionRegex.containsMatchIn(block)
            val photosSizeJson = tag(block, "metadata-photos-size")
            var width: Int? = null
            var height: Int? = null
            if (photosSizeJson != null) {
                runCatching {
                    val obj = sharedJson.parseToJsonElement(photosSizeJson).jsonObject
                    width = obj["width"]?.jsonPrimitive?.intOrNull
                    height = obj["height"]?.jsonPrimitive?.intOrNull
                }
            }
            files.add(
                NextcloudFile(
                    href = unescape(tag(block, "href")) ?: "",
                    displayName = unescape(tag(block, "displayname")),
                    contentType = unescape(tag(block, "getcontenttype")),
                    contentLength = tag(block, "getcontentlength")?.toLongOrNull(),
                    lastModified = unescape(tag(block, "getlastmodified")),
                    fileId = unescape(tag(block, "fileid")),
                    permissions = unescape(tag(block, "permissions")),
                    isCollection = isCollection,
                    hasPreview = tag(block, "has-preview")?.let { it.lowercase() == "true" || it == "1" } ?: false,
                    width = width,
                    height = height,
                    originalDateTime = unescape(tag(block, "metadata-photos-original_date_time")),
                    originalName = unescape(tag(block, "original-name")),
                    nbItems = tag(block, "nbItems")?.toIntOrNull(),
                    dateRange = unescape(tag(block, "dateRange")),
                    location = unescape(tag(block, "location"))
                )
            )
        }
        return files
    }

    private fun tag(block: String, localName: String): String? {
        val regex = Regex(
            "<[^>]*:?" + Regex.escape(localName) + "[^>]*>(.*?)</[^>]*:?" + Regex.escape(localName) + "\\s*>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return regex.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
