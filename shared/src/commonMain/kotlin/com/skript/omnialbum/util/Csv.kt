package com.skript.omnialbum.util

import com.skript.omnialbum.model.DisplayAlbum
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.providers.ProviderRegistry
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object Csv {

    fun export(albums: List<DisplayAlbum>): String {
        val sb = StringBuilder("albumName,shareLink,assetCount,allowUpload,allowDownload,provider\n")
        for (album in albums) {
            val name = album.albumName.replace("\"", "\"\"")
            val link = reconstructShareURL(album).replace("\"", "\"\"")
            sb.append("\"$name\",\"$link\",${album.assetCount ?: 0},${album.allowUpload},${album.allowDownload},${album.provider.name.lowercase()}\n")
        }
        return sb.toString()
    }

    fun reconstructShareURL(album: DisplayAlbum): String = when (album.provider) {
        PhotoProvider.IMMICH -> "${album.baseURL}/share/${album.shareKey}"
        PhotoProvider.NEXTCLOUD -> "${album.baseURL}/apps/photos/public/${album.shareKey}"
        PhotoProvider.PHOTOPRISM -> {
            val linkToken = album.shareKey.split('|').firstOrNull() ?: album.shareKey
            "${album.baseURL}/s/$linkToken"
        }
        PhotoProvider.LYCHEE -> "${album.baseURL}/gallery/${album.shareKey}"
        PhotoProvider.ENTE -> {
            val parts = album.shareKey.split('|')
            if (parts.size >= 2) "https://albums.ente.io?t=${parts[0]}#${parts[1]}"
            else "${album.baseURL}/${album.shareKey}"
        }
    }

    data class ImportedLink(val url: String, val provider: PhotoProvider)

    fun parseImport(content: String): List<ImportedLink> {
        val lines = content.split('\n')
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first()).map { it.lowercase().trim() }
        val isNewFormat = header.contains("sharelink")
        val results = mutableListOf<ImportedLink>()
        for ((index, line) in lines.withIndex()) {
            if (index == 0 || line.isBlank()) continue
            val fields = parseLine(line)
            if (isNewFormat) {
                if (fields.size < 2) continue
                val shareLink = fields[1]
                if (shareLink.isEmpty()) continue
                val provider = fields.getOrNull(5)?.let { PhotoProvider.entries.firstOrNull { p -> p.name.lowercase() == it } }
                    ?: ProviderRegistry.shared.detectProvider(shareLink)
                    ?: PhotoProvider.IMMICH
                results.add(ImportedLink(shareLink, provider))
            } else {
                if (fields.size < 4) continue
                val baseURL = fields[1]
                val shareKey = fields[2]
                val provider = fields.getOrNull(7)?.let { PhotoProvider.entries.firstOrNull { p -> p.name.lowercase() == it } }
                    ?: PhotoProvider.IMMICH
                results.add(ImportedLink(reconstructFromFields(baseURL, shareKey, provider), provider))
            }
        }
        return results
    }

    private fun reconstructFromFields(baseURL: String, shareKey: String, provider: PhotoProvider): String =
        when (provider) {
            PhotoProvider.IMMICH -> "$baseURL/share/$shareKey"
            PhotoProvider.NEXTCLOUD -> "$baseURL/apps/photos/public/$shareKey"
            PhotoProvider.PHOTOPRISM -> "$baseURL/s/${shareKey.split('|').firstOrNull() ?: shareKey}"
            PhotoProvider.LYCHEE -> "$baseURL/gallery/$shareKey"
            PhotoProvider.ENTE -> {
                val parts = shareKey.split('|')
                if (parts.size >= 2) "https://albums.ente.io?t=${parts[0]}#${parts[1]}" else "$baseURL/$shareKey"
            }
        }

    fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i += 2
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                c == ',' && !inQuotes -> {
                    fields.add(sb.toString())
                    sb.setLength(0)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        fields.add(sb.toString())
        return fields
    }

    @OptIn(ExperimentalTime::class)
    fun exportFileName(): String {
        val date = Clock.System.now().toString().substring(0, 10)
        return "omnialbum-albums-$date.csv"
    }
}
