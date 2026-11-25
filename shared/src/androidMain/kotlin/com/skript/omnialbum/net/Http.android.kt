package com.skript.omnialbum.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpClient(): HttpClient = configuredHttpClient(OkHttp.create())
