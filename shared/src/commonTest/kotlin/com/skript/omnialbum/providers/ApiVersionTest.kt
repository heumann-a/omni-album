package com.skript.omnialbum.providers

import com.skript.omnialbum.net.ApiVersion
import com.skript.omnialbum.net.ApiVersionCatalog
import com.skript.omnialbum.net.UnsupportedApiVersionException
import com.skript.omnialbum.providers.ente.EnteApiVersion
import com.skript.omnialbum.providers.ente.EnteProvider
import com.skript.omnialbum.providers.immich.ImmichApiVersion
import com.skript.omnialbum.providers.immich.ImmichProvider
import com.skript.omnialbum.providers.lychee.LycheeApiVersion
import com.skript.omnialbum.providers.lychee.LycheeProvider
import com.skript.omnialbum.providers.nextcloud.NextcloudApiVersion
import com.skript.omnialbum.providers.nextcloud.NextcloudProvider
import com.skript.omnialbum.providers.photoprism.PhotoPrismApiVersion
import com.skript.omnialbum.providers.photoprism.PhotoPrismProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiVersionTest {

    private enum class FakeVersion(
        override val id: String,
        override val isCurrent: Boolean,
        override val pathPrefix: String
    ) : ApiVersion {
        V1("v1", false, "/api"),
        V2("v2", true, "/api/v2")
    }

    @Test
    fun catalog_currentIsUniqueAndResolveFallsBackToCurrent() {
        val catalog = ApiVersionCatalog(FakeVersion.values().toList())
        assertEquals("v2", catalog.current.id)
        assertEquals(listOf("v1", "v2"), catalog.supportedIds)
        assertEquals("v2", catalog.resolve(null).id)
        assertEquals("v1", catalog.resolve("v1").id)
        assertTrue(catalog.isSupported("v1"))
        assertFalse(catalog.isSupported("v3"))
        assertFailsWith<UnsupportedApiVersionException> { catalog.requireSupported("v3") }
    }

    @Test
    fun catalog_requiresExactlyOneCurrentVersion() {
        assertFailsWith<IllegalArgumentException> {
            ApiVersionCatalog(listOf(FakeVersion.V1, FakeVersion.V2.copyAsCurrent()))
        }
    }

    private fun FakeVersion.copyAsCurrent(): FakeVersion = when (this) {
        FakeVersion.V1 -> FakeVersion.V2
        FakeVersion.V2 -> FakeVersion.V1
    }

    @Test
    fun lychee_supportsLegacyV1AndCurrentV2() {
        assertEquals("v2", LycheeApiVersion.catalog.current.id)
        assertEquals("/api/v2", LycheeApiVersion.catalog.current.pathPrefix)
        assertEquals("/api", LycheeApiVersion.V1.pathPrefix)
        assertFalse(LycheeApiVersion.V1.isCurrent)
        val provider = LycheeProvider()
        assertEquals(listOf("v1", "v2"), provider.supportedApiVersions)
        assertEquals("v2", provider.currentApiVersion)
        assertEquals("v2", provider.selectedApiVersion)
        provider.selectedApiVersion = "v1"
        assertEquals("v1", provider.selectedApiVersion)
        assertFailsWith<UnsupportedApiVersionException> { provider.selectedApiVersion = "v3" }
    }

    @Test
    fun allProviders_exposeCurrentVersionAndRejectUnknown() {
        val providers: List<ShareLinkProvider> = listOf(
            ImmichProvider(),
            NextcloudProvider(),
            PhotoPrismProvider(),
            LycheeProvider(),
            EnteProvider()
        )
        for (provider in providers) {
            assertTrue(provider.currentApiVersion in provider.supportedApiVersions)
            assertEquals(provider.currentApiVersion, provider.selectedApiVersion)
            assertFailsWith<UnsupportedApiVersionException> {
                provider.selectedApiVersion = "v999"
            }
        }
        assertEquals("/api", ImmichApiVersion.catalog.current.pathPrefix)
        assertEquals("/api/v1", PhotoPrismApiVersion.catalog.current.pathPrefix)
        assertEquals("", NextcloudApiVersion.catalog.current.pathPrefix)
        assertEquals("", EnteApiVersion.catalog.current.pathPrefix)
    }
}
