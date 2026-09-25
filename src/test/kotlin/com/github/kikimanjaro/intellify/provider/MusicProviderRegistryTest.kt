package com.github.kikimanjaro.intellify.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MusicProviderRegistryTest {

    private fun provider(id: String): MusicProvider = object : MusicProvider {
        override val id: String = id
        override val displayName: String = id
        override val capabilities: Set<ProviderCapability> = emptySet()
        override fun isConfigured(): Boolean = true
        override fun fetchState(): TrackInfo? = null
        override fun describeStatus(): String = "ok"
    }

    @Test
    fun `the provider selected in the settings is used`() {
        val spotify = provider("spotify")
        val playerctl = provider("playerctl")
        assertSame(playerctl, resolveProvider(listOf(spotify, playerctl), "playerctl"))
    }

    @Test
    fun `an empty or unknown selection falls back to the first provider`() {
        val spotify = provider("spotify")
        val playerctl = provider("playerctl")
        val all = listOf(spotify, playerctl)
        assertSame(spotify, resolveProvider(all, null))
        assertSame(spotify, resolveProvider(all, ""))
        assertSame(spotify, resolveProvider(all, "does-not-exist"))
    }

    @Test
    fun `no provider at all resolves to null`() {
        assertNull(resolveProvider(emptyList<MusicProvider>(), "spotify"))
    }

    @Test
    fun `the registry ships spotify and keeps it as the default`() {
        assertEquals("spotify", MusicProviderRegistry.defaultId)
        assertEquals("spotify", MusicProviderRegistry.active().id)
        assertNull(MusicProviderRegistry.byId("does-not-exist"))
        assertSame(MusicProviderRegistry.all().first(), MusicProviderRegistry.byId("spotify"))
    }
}
