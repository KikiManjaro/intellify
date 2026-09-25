package com.github.kikimanjaro.intellify.provider.spotify

import com.github.kikimanjaro.intellify.provider.ProviderCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyProviderTest {

    @Test
    fun `spotify stays the default provider and keeps all its capabilities`() {
        assertEquals("spotify", SpotifyProvider.id)
        assertEquals("Spotify", SpotifyProvider.displayName)
        assertEquals(
            setOf(
                ProviderCapability.CONTROL,
                ProviderCapability.SEEK,
                ProviderCapability.ARTWORK,
                ProviderCapability.POSITION
            ),
            SpotifyProvider.capabilities
        )
    }

    @Test
    fun `a mapped track carries everything the ui needs`() {
        val track = spotifyTrackInfo(
            song = "Song",
            artist = "Artist",
            album = "Album",
            artworkUrl = "https://i.scdn.co/image/cover.png",
            durationMs = 180_000L,
            positionMs = 42_000L,
            isPlaying = true,
            providerId = SpotifyProvider.id,
        )
        assertTrue(track != null)
        track!!
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals("https://i.scdn.co/image/cover.png", track.artworkUrl)
        assertEquals(180_000L, track.durationMs)
        assertEquals(42_000L, track.positionMs)
        assertTrue(track.isPlaying)
        assertEquals("Song - Artist", track.displayLabel)
        assertEquals("spotify", track.providerId)
    }

    @Test
    fun `nothing playing maps to null`() {
        assertNull(spotifyTrackInfo("", "", "", "", 0L, 0L, false, SpotifyProvider.id))
    }

    @Test
    fun `values Spotify does not know become null instead of empty strings`() {
        val track = spotifyTrackInfo(
            song = "Song",
            artist = "",
            album = "",
            artworkUrl = "",
            durationMs = 0L,
            positionMs = 0L,
            isPlaying = false,
            providerId = SpotifyProvider.id,
        )
        assertTrue(track != null)
        track!!
        assertNull(track.artist)
        assertNull(track.album)
        assertNull(track.artworkUrl)
        assertNull(track.durationMs)
        assertEquals(0L, track.positionMs)
        assertEquals("Song", track.displayLabel)
    }
}
