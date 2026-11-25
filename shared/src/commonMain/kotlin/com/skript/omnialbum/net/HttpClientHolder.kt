package com.skript.omnialbum.net

import io.ktor.client.HttpClient

object HttpClientHolder {
    val client: HttpClient by lazy { createHttpClient() }
}
