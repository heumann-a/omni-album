package com.skript.omnialbum.providers.ente

import com.skript.omnialbum.net.HttpClientHolder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.body
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class EnteServiceError(message: String) : Exception(message) {
    companion object {
        val invalidBaseURL = EnteServiceError("Invalid Ente API URL.")
        val invalidResponse = EnteServiceError("Invalid response from Ente server.")
        val passwordRequired = EnteServiceError("This shared album requires a password.")
        val decryptionFailed = EnteServiceError("Failed to decrypt album data.")
        val linkExpired = EnteServiceError("This share link has expired.")
        val noFilesFound = EnteServiceError("No files found in this shared album.")

        fun httpError(code: Int) = EnteServiceError("Ente server returned error $code.")
    }
}

object EnteService {
    private const val apiBase = "https://api.ente.io"

    private val client get() = HttpClientHolder.client

    suspend fun fetchCollectionInfo(accessToken: String): EnteCollection {
        val response = client.get("$apiBase/public-collection/info") {
            header("X-Auth-Access-Token", accessToken)
            header(HttpHeaders.Accept, "application/json")
        }
        if (response.status == HttpStatusCode.Gone) throw EnteServiceError.linkExpired
        if (!response.status.isSuccess()) throw EnteServiceError.httpError(response.status.value)
        return response.body<EnteCollectionResponse>().collection
    }

    suspend fun fetchFiles(accessToken: String): List<EnteFile> {
        val allFiles = mutableListOf<EnteFile>()
        var sinceTime = 0L
        var hasMore = true
        while (hasMore) {
            val response = client.get("$apiBase/public-collection/diff?sinceTime=$sinceTime") {
                header("X-Auth-Access-Token", accessToken)
                header(HttpHeaders.Accept, "application/json")
            }
            if (!response.status.isSuccess()) throw EnteServiceError.httpError(response.status.value)
            val diffResponse: EnteDiffResponse = response.body()
            allFiles.addAll(diffResponse.diff)
            hasMore = diffResponse.hasMore
            val last = diffResponse.diff.lastOrNull()
            if (hasMore && last?.updationTime != null) {
                sinceTime = last.updationTime
            } else {
                hasMore = false
            }
        }
        return allFiles
    }

    suspend fun downloadThumbnail(fileId: Long, accessToken: String): ByteArray {
        val response = client.get("$apiBase/public-collection/files/preview/$fileId") {
            header("X-Auth-Access-Token", accessToken)
        }
        if (!response.status.isSuccess()) throw EnteServiceError.httpError(response.status.value)
        return response.bodyAsBytes()
    }

    suspend fun downloadFile(fileId: Long, accessToken: String): ByteArray {
        val response = client.get("$apiBase/public-collection/files/download/$fileId") {
            header("X-Auth-Access-Token", accessToken)
        }
        if (!response.status.isSuccess()) throw EnteServiceError.httpError(response.status.value)
        return response.bodyAsBytes()
    }
}
