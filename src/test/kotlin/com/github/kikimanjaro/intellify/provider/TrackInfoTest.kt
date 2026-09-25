package com.github.kikimanjaro.intellify.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TrackInfoTest {

    @Test
    fun `display label joins title and artist`() {
        assertEquals(
            "Song - Artist",
            TrackInfo(title = "Song", artist = "Artist", providerId = "spotify").displayLabel
        )
    }

    @Test
    fun `display label is the title alone when there is no usable artist`() {
        assertEquals("Song", TrackInfo(title = "Song", providerId = "p").displayLabel)
        assertEquals("Song", TrackInfo(title = "Song", artist = "", providerId = "p").displayLabel)
        assertEquals("Song", TrackInfo(title = "Song", artist = "   ", providerId = "p").displayLabel)
    }

    @Test
    fun `everything the source cannot expose defaults to null or false`() {
        val track = TrackInfo(title = "Song", providerId = "p")
        assertNull(track.artist)
        assertNull(track.album)
        assertNull(track.artworkUrl)
        assertNull(track.durationMs)
        assertNull(track.positionMs)
        assertFalse(track.isPlaying)
    }
}
