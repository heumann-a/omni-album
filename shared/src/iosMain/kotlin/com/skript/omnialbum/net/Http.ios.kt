package com.skript.omnialbum.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpClient(): HttpClient = configuredHttpClient(Darwin.create())
