package com.skript.omnialbum.providers

import com.skript.omnialbum.model.PhotoProvider

class ProviderRegistry private constructor() {
    private val providers = mutableMapOf<PhotoProvider, ShareLinkProvider>()

    fun register(provider: ShareLinkProvider) {
        providers[provider.providerType] = provider
    }

    fun provider(forUrl: String): ShareLinkProvider? =
        detectionOrder.firstNotNullOfOrNull { type ->
            providers[type]?.takeIf { it.canHandle(forUrl) }
        }

    fun provider(forType: PhotoProvider): ShareLinkProvider? = providers[forType]

    fun detectProvider(from: String): PhotoProvider? =
        detectionOrder.firstOrNull { type -> providers[type]?.canHandle(from) == true }

    companion object {
        val shared = ProviderRegistry()

        private val detectionOrder = listOf(
            PhotoProvider.ENTE,
            PhotoProvider.NEXTCLOUD,
            PhotoProvider.LYCHEE,
            PhotoProvider.IMMICH,
            PhotoProvider.PHOTOPRISM
        )
    }
}
