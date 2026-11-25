package com.skript.omnialbum.providers.nextcloud

import com.skript.omnialbum.net.HttpClientHolder
import com.skript.omnialbum.net.buildUrl
import com.skript.omnialbum.net.normalizeBaseURL
import io.ktor.client.request.header
import io.ktor.client.request.onUpload
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64

class NextcloudServiceError(message: String, val httpStatus: Int? = null) : Exception(message) {
    companion object {
        val invalidResponse = NextcloudServiceError("Invalid response from server.")
        val xmlParsingError = NextcloudServiceError("Failed to parse server response.")
        val passwordRequired = NextcloudServiceError("This album requires a password.")
        val emptyAlbum = NextcloudServiceError("This album contains no photos.")

        fun invalidBaseURL(url: String) = NextcloudServiceError("Invalid server URL: $url")

        fun httpError(status: Int, message: String?): NextcloudServiceError {
            val msg = message?.takeIf { it.isNotBlank() }
            return NextcloudServiceError(
                if (msg != null) "Server error ($status): $msg" else "Server error: $status",
                httpStatus = status
            )
        }
    }
}

class NextcloudService(private val client: io.ktor.client.HttpClient = HttpClientHolder.client) {

    suspend fun fetchAlbumInfo(baseURL: String, token: String, password: String? = null): NextcloudFile {
        val files = propfind(baseURL, token, password, depth = "0")
        return files.firstOrNull() ?: throw NextcloudServiceError.invalidResponse
    }

    suspend fun listFiles(baseURL: String, token: String, password: String? = null): List<NextcloudFile> =
        propfind(baseURL, token, password, depth = "1").drop(1).filter { it.isMedia }

    suspend fun uploadFile(
        baseURL: String,
        token: String,
        password: String?,
        fileName: String,
        data: ByteArray,
        progressHandler: (Double) -> Unit
    ) {
        val base = normalizeBaseURL(baseURL)
            ?: throw NextcloudServiceError.invalidBaseURL(baseURL)
        val url = buildUrl(
            base,
            "/public.php/dav/files/$token/${com.skript.omnialbum.net.encodePathSegment(fileName)}"
        )
        val response = client.prepareRequest(url) {
            method = HttpMethod.Put
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.Authorization, basicAuth(token, password))
            header(HttpHeaders.ContentType, "application/octet-stream")
            setBody(data)
            onUpload { bytesSentTotal, contentLength ->
                if (contentLength > 0) progressHandler(minOf(bytesSentTotal.toDouble() / contentLength, 0.99))
            }
        }.execute()
        if (response.status != HttpStatusCode.OK && !(response.status.value in 200..299)) {
            throw NextcloudServiceError.httpError(response.status.value, "Upload failed")
        }
        progressHandler(1.0)
    }

    suspend fun probeAlbumExists(baseURL: String, token: String, password: String?): Boolean {
        val base = normalizeBaseURL(baseURL)
            ?: throw NextcloudServiceError.invalidBaseURL(baseURL)
        val url = buildUrl(base, "/remote.php/dav/photospublic/$token")
        val response = client.request(url) {
            method = HttpMethod("PROPFIND")
            header("Depth", "0")
            header(HttpHeaders.ContentType, "application/xml")
            header(HttpHeaders.Authorization, basicAuth(token, password))
            setBody(buildPropfindBody())
        }
        return response.status.value == 207 || response.status.value in 200..299
    }

    private suspend fun propfind(
        baseURL: String,
        token: String,
        password: String?,
        depth: String
    ): List<NextcloudFile> {
        val base = normalizeBaseURL(baseURL)
            ?: throw NextcloudServiceError.invalidBaseURL(baseURL)
        val url = buildUrl(base, "/remote.php/dav/photospublic/$token")
        val response = client.request(url) {
            method = HttpMethod("PROPFIND")
            header("Depth", depth)
            header(HttpHeaders.ContentType, "application/xml")
            header(HttpHeaders.Authorization, basicAuth(token, password))
            setBody(buildPropfindBody())
        }
        val body = response.bodyAsText()
        if (response.status == HttpStatusCode.Unauthorized) throw NextcloudServiceError.passwordRequired
        if (response.status.value != 207 && response.status.value !in 200..299) {
            throw NextcloudServiceError.httpError(response.status.value, body)
        }
        return WebDavParser.parse(body)
    }

    private fun basicAuth(token: String, password: String?): String {
        val authString = "$token:${password ?: ""}"
        return "Basic " + Base64.encode(authString.encodeToByteArray())
    }

    private fun buildPropfindBody(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:propfind xmlns:d="DAV:"
                    xmlns:oc="http://owncloud.org/ns"
                    xmlns:nc="http://nextcloud.org/ns"
                    xmlns:ocs="http://open-collaboration-services.org/ns">
            <d:prop>
                <d:getcontentlength />
                <d:getcontenttype />
                <d:getetag />
                <d:getlastmodified />
                <d:resourcetype />
                <oc:fileid />
                <oc:permissions />
                <nc:has-preview />
                <nc:metadata-photos-size />
                <nc:metadata-photos-original_date_time />
                <nc:original-name />
                <nc:nbItems />
                <nc:dateRange />
                <nc:location />
            </d:prop>
        </d:propfind>
    """.trimIndent()
}
