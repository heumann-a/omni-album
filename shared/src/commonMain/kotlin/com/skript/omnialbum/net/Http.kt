package com.skript.omnialbum.net

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpCookies
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val sharedJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

expect fun createHttpClient(): HttpClient

fun configuredHttpClient(engine: io.ktor.client.engine.HttpClientEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json(sharedJson) }
        install(HttpCookies)
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 600_000
        }
    }
