package com.github.kikimanjaro.intellify.provider.spotify

import com.github.kikimanjaro.intellify.provider.MusicProvider
import com.github.kikimanjaro.intellify.provider.ProviderCapability
import com.github.kikimanjaro.intellify.provider.TrackInfo
import com.github.kikimanjaro.intellify.services.SpotifyCredentials
import com.github.kikimanjaro.intellify.services.SpotifyService

/**
 * [MusicProvider] backed by the Spotify Web API (OAuth).
 *
 * This is exactly the behaviour the plugin has always had: [SpotifyService] keeps owning the OAuth
 * flow, the token store and the Web API calls, and this adapter exposes them through the SPI. It is
 * the default provider, so nothing changes for existing users.
 */
object SpotifyProvider : MusicProvider {
    override val id: String = ID
    override val displayName: String = "Spotify"
    override val capabilities: Set<ProviderCapability> = ProviderCapability.ALL

    override fun isConfigured(): Boolean = SpotifyCredentials.isConfigured

    override fun fetchState(): TrackInfo? {
        if (!isConfigured()) return null
        // Same call as before, and it also drives the OAuth flow: when no authorization code is
        // stored yet, SpotifyService opens the browser and the user signs in. Keeping that side
        // effect here preserves the historical behaviour for the default provider.
        SpotifyService.getInformationAboutUsersCurrentPlayingTrack()
        return spotifyTrackInfo(
            song = SpotifyService.song,
            artist = SpotifyService.artist,
            album = SpotifyService.album,
            artworkUrl = SpotifyService.imageUrl,
            durationMs = SpotifyService.durationMs.toLong(),
            positionMs = SpotifyService.progressInMs.toLong(),
            isPlaying = SpotifyService.isPlaying,
            providerId = id,
        )
    }

    override fun playPause() {
        if (SpotifyService.isPlaying) SpotifyService.pauseTrack() else SpotifyService.startTrack()
    }

    override fun next() = SpotifyService.nextTrack()

    override fun previous() = SpotifyService.prevTrack()

    override fun seek(positionMs: Long) = SpotifyService.setProgress(positionMs.toInt())

    /**
     * Clearing the credentials is enough: the next poll finds no authorization code and restarts
     * the browser sign-in, which is what the "Change account" action used to do.
     */
    override fun signOut() = SpotifyService.clearCredentials()

    override fun describeStatus(): String = when {
        !isConfigured() -> SpotifyCredentials.configurationProblem ?: "Spotify is not configured"
        SpotifyService.title.isNotEmpty() -> "Signed in, playing: ${SpotifyService.title}"
        else -> "Signed in, nothing playing"
    }

    const val ID: String = "spotify"
}

/**
 * Maps the raw Spotify values to the provider-agnostic model.
 *
 * Pure on purpose: it is the only part of the Spotify integration that can be unit tested without a
 * running IDE. Returns `null` when nothing is playing (Spotify leaves the fields empty then).
 */
internal fun spotifyTrackInfo(
    song: String,
    artist: String,
    album: String,
    artworkUrl: String,
    durationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    providerId: String,
): TrackInfo? {
    if (song.isBlank() && !isPlaying) return null
    return TrackInfo(
        title = song,
        artist = artist.ifBlank { null },
        album = album.ifBlank { null },
        artworkUrl = artworkUrl.ifBlank { null },
        durationMs = durationMs.takeIf { it > 0L },
        positionMs = positionMs.takeIf { it >= 0L },
        isPlaying = isPlaying,
        providerId = providerId,
    )
}
