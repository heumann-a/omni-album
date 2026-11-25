package com.skript.omnialbum.providers

import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.providers.ente.EnteCrypto
import com.skript.omnialbum.providers.immich.ImmichProvider
import com.skript.omnialbum.providers.lychee.LycheeProvider
import com.skript.omnialbum.providers.nextcloud.NextcloudProvider
import com.skript.omnialbum.providers.photoprism.PhotoPrismProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderParseTest {

    @Test
    fun registry_detectsAllProvidersInDeterministicOrder() {
        registerAllProviders()
        assertEquals(
            PhotoProvider.ENTE,
            ProviderRegistry.shared.detectProvider("https://albums.ente.io/?t=abc#key")
        )
        assertEquals(
            PhotoProvider.NEXTCLOUD,
            ProviderRegistry.shared.detectProvider("https://cloud.example.com/apps/photos/public/TOKEN")
        )
        assertEquals(
            PhotoProvider.LYCHEE,
            ProviderRegistry.shared.detectProvider("https://photos.example.com/gallery/01890abc-1234")
        )
        assertEquals(
            PhotoProvider.IMMICH,
            ProviderRegistry.shared.detectProvider("https://immich.example.com/share/KEY123")
        )
        assertEquals(
            PhotoProvider.PHOTOPRISM,
            ProviderRegistry.shared.detectProvider("https://pp.example.com/s/token123/slug")
        )
        assertNull(ProviderRegistry.shared.detectProvider("https://example.com/nothing"))
    }

    @Test
    fun immich_parsesPathKeyAndSlugKey() {
        val provider = ImmichProvider()
        val direct = provider.parseShareLink("https://immich.example.com/share/KEY123")
        assertEquals("https://immich.example.com", direct.baseURL)
        assertEquals("KEY123", direct.key)
        assertEquals("KEY123", direct.token)
        assertNull(direct.slug)

        val slugged = provider.parseShareLink("https://immich.example.com:8443/share/my-slug?key=KEY999")
        assertEquals("https://immich.example.com:8443", slugged.baseURL)
        assertEquals("KEY999", slugged.key)
        assertEquals("my-slug", slugged.slug)
        assertNull(slugged.token)
    }

    @Test
    fun nextcloud_parsesToken() {
        val provider = NextcloudProvider()
        val parsed = provider.parseShareLink("https://cloud.example.com/apps/photos/public/AbCdEf123")
        assertEquals("https://cloud.example.com", parsed.baseURL)
        assertEquals("AbCdEf123", parsed.token)
    }

    @Test
    fun photoprism_parsesTokenAndSlug_butNotImmichOrNextcloud() {
        val provider = PhotoPrismProvider()
        assertTrue(provider.canHandle("https://pp.example.com/s/tok/slug"))
        assertFalse(provider.canHandle("https://immich.example.com/share/KEY"))
        assertFalse(provider.canHandle("https://cloud.example.com/apps/photos/public/TOKEN"))
        val parsed = provider.parseShareLink("http://pp.local:2342/s/tok123/my-album")
        assertEquals("http://pp.local:2342", parsed.baseURL)
        assertEquals("tok123", parsed.key)
        assertEquals("my-album", parsed.slug)
    }

    @Test
    fun lychee_parsesAlbumId() {
        val provider = LycheeProvider()
        val parsed = provider.parseShareLink("https://photos.example.com/gallery/abc-123")
        assertEquals("https://photos.example.com", parsed.baseURL)
        assertEquals("abc-123", parsed.key)
    }

    @Test
    fun ente_parsesAccessTokenAndFragment() {
        val provider = com.skript.omnialbum.providers.ente.EnteProvider()
        assertTrue(provider.canHandle("https://albums.ente.io/?t=tok#key"))
        assertFalse(provider.canHandle("https://example.com/?t=tok#key"))
        val parsed = provider.parseShareLink("https://albums.ente.io/?t=ACCESS#BASE58KEY")
        assertEquals("https://api.ente.io", parsed.baseURL)
        assertEquals("ACCESS", parsed.token)
        assertEquals("BASE58KEY", parsed.key)
    }

    @Test
    fun enteBase58_decodesKnownVector() {
        assertEquals(
            "Hello World!".encodeToByteArray().toList(),
            EnteCrypto.decodeBase58("2NEpo7TZRRrLZSi2U")?.toList()
        )
    }

    @Test
    fun enteBase58_handlesLeadingZerosAndRejectsInvalid() {
        val decoded = EnteCrypto.decodeBase58("1") 
        assertNotNull(decoded)
        assertEquals(1, decoded.size)
        assertEquals(0, decoded[0].toInt())
        assertNull(EnteCrypto.decodeBase58("0OIl"))
    }
}
