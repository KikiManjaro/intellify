package com.github.kikimanjaro.intellify.provider

/**
 * Provider-agnostic snapshot of what a music source is doing right now.
 *
 * Fields are nullable on purpose: a provider that cannot expose a value (no position, no artwork,
 * no duration) leaves them `null` and the UI adapts, see [ProviderCapability].
 */
data class TrackInfo(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    /** `http(s)` or `file:` URL, `null` when unknown. */
    val artworkUrl: String? = null,
    /** `null` when the source does not expose it. */
    val durationMs: Long? = null,
    /** `null` when the source does not expose it. */
    val positionMs: Long? = null,
    val isPlaying: Boolean = false,
    /** Id of the [MusicProvider] that produced this snapshot. */
    val providerId: String,
) {
    /** "Title - Artist", the label shown in the status bar. */
    val displayLabel: String
        get() = if (artist.isNullOrBlank()) title else "$title - $artist"
}

/**
 * What a provider is able to do; the UI hides the controls it cannot honour.
 *
 * Deliberately **not** an `enum class`: Kotlin 2.4.10 gives every enum a `$ENTRIES` static field
 * initialised through `kotlin.enums.EnumEntriesKt`, which does not exist in the kotlin-stdlib the
 * target IntelliJ platform bundles (the plugin targets `since-build 211`, and the build sets
 * `kotlin.stdlib.default.dependency = false`, so no stdlib is shipped with the plugin). Loading such
 * a class fails with `NoClassDefFoundError: kotlin/enums/EnumEntriesKt` — reproduced by running the
 * test suite. These singletons behave like enum entries and never touch that API.
 */
class ProviderCapability private constructor(private val id: String) {
    override fun toString(): String = id

    companion object {
        /** play/pause, next, previous */
        val CONTROL: ProviderCapability = ProviderCapability("CONTROL")

        /** [MusicProvider.seek] is supported and meaningful. */
        val SEEK: ProviderCapability = ProviderCapability("SEEK")

        /** [TrackInfo.artworkUrl] is exposed. */
        val ARTWORK: ProviderCapability = ProviderCapability("ARTWORK")

        /** [TrackInfo.positionMs] is exposed. */
        val POSITION: ProviderCapability = ProviderCapability("POSITION")

        /** Every capability, for providers that support all of them. */
        val ALL: Set<ProviderCapability> = setOf(CONTROL, SEEK, ARTWORK, POSITION)
    }
}

/**
 * A music source Intellify can display, and control when it supports it.
 *
 * Implementations must be defensive: [fetchState] and [describeStatus] run on a background thread
 * from the status bar updater and from the settings page, so they must never throw — return `null`
 * (nothing playing, source unreachable, not configured) instead.
 */
interface MusicProvider {
    /** Stable machine id, stored in the settings. */
    val id: String

    /** Human readable name, shown in the settings combo box. */
    val displayName: String

    val capabilities: Set<ProviderCapability>

    /** `false` when the provider lacks its credentials/configuration; the UI then degrades gracefully. */
    fun isConfigured(): Boolean

    /** Returns `null` when nothing is playing / the source is unreachable. Must never throw. */
    fun fetchState(): TrackInfo?

    fun playPause() {}

    fun next() {}

    fun previous() {}

    fun seek(positionMs: Long) {}

    /** Called by the "Change account" action; clears stored credentials. No-op by default. */
    fun signOut() {}

    /** Human-readable probe of the current state, for the settings page. Must never throw. */
    fun describeStatus(): String
}
