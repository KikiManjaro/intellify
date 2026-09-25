package com.github.kikimanjaro.intellify.provider.macos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacosMediaProviderTest {

    private val sep = MAC_FIELD_SEPARATOR

    @Test
    fun `osascript receives the script with -e`() {
        assertEquals(listOf("osascript", "-e", "return 1"), osascriptCommand("return 1"))
    }

    @Test
    fun `the state script asks the app for every field the parser reads`() {
        val script = stateScript(SPOTIFY_APP)
        assertTrue(script.contains("tell application \"Spotify\""))
        for (property in listOf(
            "player state as string",
            "name of current track",
            "artist of current track",
            "album of current track",
            "duration of current track",
            "player position",
        )) {
            assertTrue(property, script.contains(property))
        }
        assertTrue(script.contains("(ASCII character 31)"))
    }

    @Test
    fun `playback controls are built per app`() {
        assertEquals(
            "tell application \"Music\" to playpause",
            controlScript(MUSIC_APP, ACTION_PLAY_PAUSE)
        )
        assertEquals("tell application \"Music\" to next track", controlScript(MUSIC_APP, ACTION_NEXT))
        assertEquals(
            "tell application \"Music\" to previous track",
            controlScript(MUSIC_APP, ACTION_PREVIOUS)
        )
        assertEquals(
            "tell application \"Spotify\" to playpause",
            controlScript(SPOTIFY_APP, ACTION_PLAY_PAUSE)
        )
        assertNull(controlScript(SPOTIFY_APP, "unknown"))
    }

    @Test
    fun `seeking sends seconds`() {
        assertEquals(
            "tell application \"Spotify\" to set player position to 42.500",
            seekScript(SPOTIFY_APP, 42_500L)
        )
    }

    @Test
    fun `the running app is detected`() {
        assertEquals(SPOTIFY_APP, parseActiveApp("Spotify\n"))
        assertEquals(MUSIC_APP, parseActiveApp("Music"))
        assertNull(parseActiveApp(""))
        assertNull(parseActiveApp("false"))
        assertNull(parseActiveApp("iTunes"))
    }

    @Test
    fun `spotify reports its duration in milliseconds, music in seconds`() {
        val spotify = parseState("playing${sep}Song${sep}Artist${sep}Album${sep}180000${sep}42.5", SPOTIFY_APP, "macos-media")
        assertTrue(spotify != null)
        spotify!!
        assertEquals(180_000L, spotify.durationMs)
        assertEquals(42_500L, spotify.positionMs)
        assertTrue(spotify.isPlaying)
        assertEquals("Song - Artist", spotify.displayLabel)

        val music = parseState("playing${sep}Song${sep}Artist${sep}Album${sep}180.0${sep}42.5", MUSIC_APP, "macos-media")
        assertTrue(music != null)
        assertEquals(180_000L, music!!.durationMs)
        assertEquals(42_500L, music.positionMs)
    }

    @Test
    fun `a localized decimal separator is accepted`() {
        val track = parseState("paused${sep}Song$sep$sep${sep}180,5${sep}1,25", MUSIC_APP, "macos-media")
        assertTrue(track != null)
        track!!
        assertFalse(track.isPlaying)
        assertEquals(180_500L, track.durationMs)
        assertEquals(1_250L, track.positionMs)
        assertNull(track.artist)
    }

    @Test
    fun `nothing playing or garbage maps to nothing`() {
        assertNull(parseState("", SPOTIFY_APP, "macos-media"))
        assertNull(parseState("playing", SPOTIFY_APP, "macos-media"))
        assertNull(parseState("stopped$sep$sep$sep$sep$sep", SPOTIFY_APP, "macos-media"))
    }
}
