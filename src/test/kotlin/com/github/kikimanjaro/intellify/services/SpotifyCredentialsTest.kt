package com.github.kikimanjaro.intellify.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure logic of the credential resolution, testable without a running IDE application.
 */
class SpotifyCredentialsTest {

    @Test
    fun `settings value wins over the environment`() {
        assertEquals("from-settings", resolveCredential("from-settings", "from-env"))
    }

    @Test
    fun `environment is used when the settings are blank`() {
        assertEquals("from-env", resolveCredential("", "from-env"))
        assertEquals("from-env", resolveCredential("   ", "from-env"))
        assertEquals("from-env", resolveCredential(null, "from-env"))
    }

    @Test
    fun `nothing configured resolves to null`() {
        assertNull(resolveCredential(null, null))
        assertNull(resolveCredential("", "  "))
    }

    @Test
    fun `blank values never count as configured`() {
        assertNull(resolveCredential(" ", "\t"))
    }

    @Test
    fun `missing configuration reason is null when both credentials are present`() {
        assertNull(missingConfigurationReason("id", "secret"))
    }

    @Test
    fun `missing configuration reason names what is missing`() {
        assertEquals(
            "no Spotify client ID and no Spotify client secret are configured",
            missingConfigurationReason(null, null)
        )
        assertEquals("no Spotify client ID is configured", missingConfigurationReason(null, "secret"))
        assertEquals("no Spotify client secret is configured", missingConfigurationReason("id", null))
    }

    @Test
    fun `a blank environment variable does not make the plugin configured`() {
        assertFalse(missingConfigurationReason(resolveCredential("", ""), resolveCredential(null, " ")) == null)
    }
}
