package com.skript.omnialbum.providers.lychee

import com.skript.omnialbum.net.HttpClientHolder
import com.skript.omnialbum.net.buildUrl
import com.skript.omnialbum.net.percentDecode
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LycheeServiceError(message: String, val httpStatus: Int? = null) : Exception(message) {
    companion object {
        val invalidBaseURL = LycheeServiceError("Invalid Lychee server URL.")
        val invalidResponse = LycheeServiceError("Invalid response from Lychee server.")
        val passwordRequired = LycheeServiceError("This album requires a password.")
        val albumNotFound = LycheeServiceError("Could not find the album.")
        val uploadNotSupported = LycheeServiceError("Lychee does not support uploads via share links.")

        fun httpError(status: Int, message: String?): LycheeServiceError {
            val msg = message?.takeIf { it.isNotBlank() }
            return LycheeServiceError(
                if (msg != null) "Server error ($status): $msg" else "Server error: $status",
                httpStatus = status
            )
        }
    }
}

class LycheeService(private val client: HttpClient = HttpClientHolder.client) {

    suspend fun initSession(baseURL: String, albumId: String) {
        val response = client.get(buildUrl(baseURL, "/gallery/$albumId"))
        if (!response.status.isSuccess()) throw LycheeServiceError.albumNotFound
    }

    suspend fun probeApiVersion(baseURL: String, albumId: String, apiPathPrefix: String): Int {
        val response = client.get(buildUrl(baseURL, "$apiPathPrefix/Album", listOf("album_id" to albumId))) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            xsrfToken(baseURL)?.let { header("X-XSRF-TOKEN", it) }
        }
        return response.status.value
    }

    suspend fun fetchAlbum(
        baseURL: String,
        albumId: String,
        apiPathPrefix: String = LycheeApiVersion.V2.pathPrefix
    ): LycheeAlbumResponse {
        val response = client.get(buildUrl(baseURL, "$apiPathPrefix/Album", listOf("album_id" to albumId))) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            xsrfToken(baseURL)?.let { header("X-XSRF-TOKEN", it) }
        }
        return when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                throw LycheeServiceError.passwordRequired
            response.status == HttpStatusCode.NotFound -> throw LycheeServiceError.albumNotFound
            !response.status.isSuccess() ->
                throw LycheeServiceError.httpError(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
            else -> response.body()
        }
    }

    suspend fun unlockAlbum(
        baseURL: String,
        albumId: String,
        password: String,
        apiPathPrefix: String = LycheeApiVersion.V2.pathPrefix
    ) {
        val response = client.post(buildUrl(baseURL, "$apiPathPrefix/Album::unlock")) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            xsrfToken(baseURL)?.let { header("X-XSRF-TOKEN", it) }
            setBody(Json.encodeToString(mapOf("album_id" to albumId, "password" to password)))
        }
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            throw LycheeServiceError.passwordRequired
        }
        if (!response.status.isSuccess()) {
            throw LycheeServiceError.httpError(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    private suspend fun xsrfToken(baseURL: String): String? {
        val cookies = runCatching { client.cookies(baseURL) }.getOrNull() ?: return null
        val cookie = cookies.firstOrNull { it.name == "XSRF-TOKEN" } ?: return null
        return percentDecode(cookie.value)
    }
}
