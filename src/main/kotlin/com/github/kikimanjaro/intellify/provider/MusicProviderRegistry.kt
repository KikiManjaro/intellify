package com.github.kikimanjaro.intellify.provider

import com.github.kikimanjaro.intellify.provider.spotify.SpotifyProvider
import com.github.kikimanjaro.intellify.settings.IntellifySettings

/**
 * Application-wide registry of the available [MusicProvider]s, and holder of the last state they
 * reported.
 *
 * Spotify is listed first and stays the default: existing users see no change at all.
 */
object MusicProviderRegistry {
    /**
     * Every provider the plugin ships. The order matters: the first one is the default when the
     * settings hold no (or an unknown) provider id.
     */
    private val providers: List<MusicProvider> = listOf(SpotifyProvider)

    /**
     * Last snapshot produced by [refresh], read by the status bar widget and the popup panel.
     * `null` means "nothing playing" or "the active provider is not configured".
     */
    @Volatile
    var currentTrack: TrackInfo? = null
        private set

    fun all(): List<MusicProvider> = providers

    fun byId(id: String?): MusicProvider? = providers.firstOrNull { it.id == id }

    /** Id of the provider used when the settings do not select one. */
    val defaultId: String
        get() = providers.first().id

    /** The provider selected in the settings, falling back to [defaultId]. */
    fun active(): MusicProvider =
        resolveProvider(providers, configuredId()) ?: providers.first()

    /**
     * Polls the active provider and caches the result in [currentTrack]. Never throws: a provider
     * that blows up is reported and treated as "nothing playing".
     */
    fun refresh(): TrackInfo? {
        val provider = active()
        val state = if (provider.isConfigured()) {
            runCatching { provider.fetchState() }
                .onFailure { println("Intellify: provider '${provider.id}' failed to report its state: ${it.message}") }
                .getOrNull()
        } else {
            null
        }
        currentTrack = state
        return state
    }

    private fun configuredId(): String? =
        runCatching { IntellifySettings.getInstance()?.state?.providerId }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * Pure resolution of the provider to use: the one matching [selectedId], or the first one declared
 * when nothing/anything unknown is selected. Unit-tested without a running IDE application.
 */
internal fun <T : MusicProvider> resolveProvider(providers: List<T>, selectedId: String?): T? =
    providers.firstOrNull { it.id == selectedId } ?: providers.firstOrNull()
