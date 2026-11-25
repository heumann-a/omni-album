package com.skript.omnialbum.providers.immich

import com.skript.omnialbum.net.buildUrl
import com.skript.omnialbum.net.normalizeBaseURL
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.body
import io.ktor.http.isSuccess

class ImmichServiceError(message: String) : Exception(message) {
    companion object {
        fun httpError(status: Int, message: String?): ImmichServiceError {
            val msg = message?.takeIf { it.isNotBlank() }
            return ImmichServiceError(
                if (msg != null) "Server error ($status): $msg" else "Server error: $status"
            )
        }
    }
}

class ImmichService(private val client: HttpClient) {

    suspend fun fetchSharedLink(
        baseURL: String,
        key: String? = null,
        slug: String? = null,
        token: String? = null,
        password: String? = null
    ): SharedLinkResponse {
        val base = normalizeBaseURL(baseURL)
            ?: throw ImmichServiceError("Invalid server URL: $baseURL")
        val query = mutableListOf<Pair<String, String>>()
        if (!key.isNullOrEmpty()) query.add("key" to key)
        if (!slug.isNullOrEmpty()) query.add("slug" to slug)
        if (!token.isNullOrEmpty()) query.add("token" to token)
        if (!password.isNullOrEmpty()) query.add("password" to password)
        val response = client.get(buildUrl(base, "/api/shared-links/me", query))
        return response.unwrap { it.body() }
    }

    suspend fun fetchAlbumWithAssets(
        baseURL: String,
        albumId: String,
        shareKey: String,
        password: String? = null
    ): AlbumWithAssetsResponse {
        val base = normalizeBaseURL(baseURL)
            ?: throw ImmichServiceError("Invalid server URL: $baseURL")
        val query = mutableListOf("key" to shareKey)
        if (!password.isNullOrEmpty()) query.add("password" to password)
        val response = client.get(buildUrl(base, "/api/albums/$albumId", query))
        return response.unwrap { it.body() }
    }

    private suspend inline fun <reified T> HttpResponse.unwrap(crossinline decode: suspend (HttpResponse) -> T): T {
        if (!status.isSuccess()) {
            throw ImmichServiceError.httpError(status.value, runCatching { bodyAsText() }.getOrNull())
        }
        return decode(this)
    }
}
