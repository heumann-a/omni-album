package com.skript.omnialbum.providers

import com.skript.omnialbum.providers.ente.EnteProvider
import com.skript.omnialbum.providers.immich.ImmichProvider
import com.skript.omnialbum.providers.lychee.LycheeProvider
import com.skript.omnialbum.providers.nextcloud.NextcloudProvider
import com.skript.omnialbum.providers.photoprism.PhotoPrismProvider

fun registerAllProviders(registry: ProviderRegistry = ProviderRegistry.shared) {
    registry.register(EnteProvider())
    registry.register(NextcloudProvider())
    registry.register(LycheeProvider())
    registry.register(ImmichProvider())
    registry.register(PhotoPrismProvider())
}
