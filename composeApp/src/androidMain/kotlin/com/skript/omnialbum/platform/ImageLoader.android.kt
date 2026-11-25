package com.skript.omnialbum.platform

import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.skript.omnialbum.net.HttpClientHolder

actual val appImageLoader: ImageLoader by lazy {
    val httpClientFactory: () -> io.ktor.client.HttpClient = { HttpClientHolder.client }
    ImageLoader.Builder(appContext)
        .components {
            add(KtorNetworkFetcherFactory(httpClientFactory))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizeBytes(150 * 1024 * 1024)
                .build()
        }
        .build()
}
