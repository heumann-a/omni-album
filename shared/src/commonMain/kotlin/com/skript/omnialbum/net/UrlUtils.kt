package com.skript.omnialbum.net

import io.ktor.http.Url
import io.ktor.http.encodeURLParameter

fun normalizeBaseURL(raw: String): String? {
    var s = raw.trim()
    if (!s.contains("://")) s = "https://$s"
    val url = runCatching { Url(s) }.getOrNull() ?: return null
    val host = url.host
    if (host.isEmpty()) return null
    val scheme = url.protocol.name
    var base = "$scheme://$host"
    val port = url.explicitPort
    if (port != null && !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)) {
        base += ":$port"
    }
    return base
}

fun buildUrl(base: String, path: String, query: List<Pair<String, String>> = emptyList()): String {
    val sb = StringBuilder(base.trimEnd('/'))
    sb.append(path)
    if (query.isNotEmpty()) {
        sb.append('?')
        query.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append('&')
            sb.append(k.encodeURLParameter()).append('=').append(v.encodeURLParameter())
        }
    }
    return sb.toString()
}

fun encodePathSegment(value: String): String = value.encodeURLParameter()

fun percentDecode(value: String): String {
    var out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3)
            hex.toIntOrNull(16)?.let {
                out.append(it.toChar())
                i += 3
                continue
            }
        }
        if (c == '+') out.append(' ') else out.append(c)
        i++
    }
    return out.toString()
}

fun xmlUnescape(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
