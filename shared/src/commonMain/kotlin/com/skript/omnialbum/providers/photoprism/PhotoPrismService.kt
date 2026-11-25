package com.skript.omnialbum.providers.photoprism

import com.skript.omnialbum.net.HttpClientHolder
import com.skript.omnialbum.net.buildUrl
import com.skript.omnialbum.net.normalizeBaseURL
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PhotoPrismServiceError(message: String, val httpStatus: Int? = null) : Exception(message) {
    companion object {
        fun invalidBaseURL(url: String) = PhotoPrismServiceError("Invalid server URL: $url")
        val invalidResponse = PhotoPrismServiceError("Invalid response from server.")
        val passwordRequired = PhotoPrismServiceError("This shared album requires a password.")
        val linkExpired = PhotoPrismServiceError("This share link has expired.")
        val albumNotFound = PhotoPrismServiceError("Could not find the shared album.")

        fun sessionFailed(reason: String) = PhotoPrismServiceError("Failed to create session: $reason")

        fun httpError(status: Int, message: String?): PhotoPrismServiceError {
            val msg = message?.takeIf { it.isNotBlank() }
            return PhotoPrismServiceError(
                if (msg != null) "Server error ($status): $msg" else "Server error: $status",
                httpStatus = status
            )
        }
    }
}

class PhotoPrismService(private val client: io.ktor.client.HttpClient = HttpClientHolder.client) {

    suspend fun resolveShareLink(
        baseURL: String,
        token: String,
        slug: String?,
        password: String?,
        apiPathPrefix: String = PhotoPrismApiVersion.V1.pathPrefix
    ): PhotoPrismShareInfo {
        val base = normalizeBaseURL(baseURL)
            ?: throw PhotoPrismServiceError.invalidBaseURL(baseURL)
        val albumUID = fetchAlbumUIDFromSharePage(base, token, slug)
        val session = establishSession(base, token, password, apiPathPrefix)
        return PhotoPrismShareInfo(session = session, albumUID = albumUID, albumTitle = null)
    }

    suspend fun establishSession(
        base: String,
        token: String,
        password: String?,
        apiPathPrefix: String = PhotoPrismApiVersion.V1.pathPrefix
    ): PhotoPrismSession {
        runCatching { getSession(base, apiPathPrefix) }.getOrNull()?.let { return it }
        return postSession(base, token, password, apiPathPrefix)
    }

    private suspend fun getSession(base: String, apiPathPrefix: String): PhotoPrismSession {
        val response = client.get(buildUrl(base, "$apiPathPrefix/session")) {
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) throw PhotoPrismServiceError.invalidResponse
        val sessionResponse: PhotoPrismSessionResponse = response.body()
        val previewToken = sessionResponse.config?.previewToken
        if (previewToken.isNullOrEmpty()) {
            throw PhotoPrismServiceError.sessionFailed("No preview token in public session")
        }
        val isPublic = sessionResponse.config?.public == true || sessionResponse.config?.authMode == "public"
        if (!isPublic) throw PhotoPrismServiceError.sessionFailed("Not a public instance")
        return PhotoPrismSession(
            accessToken = sessionResponse.accessToken ?: sessionResponse.id,
            previewToken = previewToken,
            downloadToken = sessionResponse.config?.downloadToken ?: "public",
            isPublic = true
        )
    }

    private suspend fun postSession(
        base: String,
        token: String,
        password: String?,
        apiPathPrefix: String
    ): PhotoPrismSession {
        val response = client.post(buildUrl(base, "$apiPathPrefix/session")) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val body = mutableMapOf("token" to token)
            if (!password.isNullOrEmpty()) body["password"] = password
            setBody(Json.encodeToString(body))
        }
        if (response.status.value == 401 || response.status.value == 403) {
            val responseStr = runCatching { response.bodyAsText() }.getOrNull()?.lowercase().orEmpty()
            if (responseStr.contains("expired")) throw PhotoPrismServiceError.linkExpired
            throw PhotoPrismServiceError.passwordRequired
        }
        if (!response.status.isSuccess()) {
            throw PhotoPrismServiceError.httpError(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        val sessionResponse: PhotoPrismSessionResponse = response.body()
        val accessToken = sessionResponse.accessToken ?: sessionResponse.id
        if (accessToken.isNullOrEmpty()) {
            throw PhotoPrismServiceError.sessionFailed("No access token in response")
        }
        return PhotoPrismSession(
            accessToken = accessToken,
            previewToken = sessionResponse.config?.previewToken ?: "public",
            downloadToken = sessionResponse.config?.downloadToken ?: "public",
            isPublic = false
        )
    }

    private suspend fun fetchAlbumUIDFromSharePage(base: String, token: String, slug: String?): String {
        val shareURL = buildUrl(base, "/s/$token/${slug ?: ""}")
        val response = client.get(shareURL)
        if (!response.status.isSuccess()) throw PhotoPrismServiceError.albumNotFound
        val html = response.bodyAsText()
        extractAlbumUID(html, token)?.let { return it }
        if (!slug.isNullOrEmpty()) return slug
        throw PhotoPrismServiceError.albumNotFound
    }

    private fun extractAlbumUID(html: String, token: String): String? {
        val regex = Regex("/s/" + Regex.escape(token) + "/([a-z0-9]+)", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)
    }

    suspend fun searchPhotos(
        baseURL: String,
        albumUID: String,
        accessToken: String?,
        count: Int = 100,
        offset: Int = 0,
        apiPathPrefix: String = PhotoPrismApiVersion.V1.pathPrefix
    ): Pair<List<PhotoPrismPhoto>, Int?> {
        val base = normalizeBaseURL(baseURL)
            ?: throw PhotoPrismServiceError.invalidBaseURL(baseURL)
        val query = listOf(
            "s" to albumUID,
            "count" to count.toString(),
            "offset" to offset.toString(),
            "merged" to "true",
            "public" to "true"
        )
        val response = client.get(buildUrl(base, "$apiPathPrefix/photos", query)) {
            accessToken?.let { header("X-Auth-Token", it) }
        }
        if (!response.status.isSuccess()) {
            throw PhotoPrismServiceError.httpError(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        val photos: List<PhotoPrismPhoto> = response.body()
        val totalCount = response.headers["X-Count"]?.toIntOrNull()
        return photos to totalCount
    }

    suspend fun fetchAllPhotos(
        baseURL: String,
        albumUID: String,
        accessToken: String?,
        apiPathPrefix: String = PhotoPrismApiVersion.V1.pathPrefix
    ): List<PhotoPrismPhoto> {
        val pageSize = 100
        val allPhotos = mutableListOf<PhotoPrismPhoto>()
        var offset = 0
        while (true) {
            val (photos, _) = searchPhotos(baseURL, albumUID, accessToken, count = pageSize, offset = offset, apiPathPrefix = apiPathPrefix)
            allPhotos.addAll(photos)
            if (photos.size < pageSize) break
            offset += pageSize
        }
        return allPhotos
    }
}
