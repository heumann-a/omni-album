package com.skript.omnialbum.deeplink

data class DeepLinkTarget(
    val albumId: String,
    val assetId: String?
)

object DeepLinkParser {

    fun parse(url: String): DeepLinkTarget? {
        if (!url.startsWith("omnialbum://")) return null
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return null
        val params = url.substring(queryStart + 1).split('&').mapNotNull { pair ->
            val kv = pair.split('=', limit = 2)
            if (kv.size == 2 && kv[0].isNotEmpty()) kv[0] to kv[1] else null
        }.toMap()
        val albumId = params["albumId"] ?: return null
        return DeepLinkTarget(albumId = albumId, assetId = params["assetId"])
    }
}
