package com.github.kikimanjaro.intellify.provider.playerctl

import com.github.kikimanjaro.intellify.provider.process.ProcessResult
import com.github.kikimanjaro.intellify.provider.process.RecordingProcessRunner
import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PlayerctlProviderTest {

    private val sep = FIELD_SEPARATOR

    @Test
    fun `the metadata command asks for every field the parser reads`() {
        assertEquals(
            listOf("playerctl", "--player=spotify", "metadata", "--format", METADATA_FORMAT),
            metadataCommand("spotify")
        )
        assertEquals(listOf("playerctl", "metadata", "--format", METADATA_FORMAT), metadataCommand(null))
        assertEquals(listOf("playerctl", "metadata", "--format", METADATA_FORMAT), metadataCommand("   "))
    }

    @Test
    fun `the format template keeps the separator between every field`() {
        assertEquals(7, METADATA_FORMAT.split(FIELD_SEPARATOR).size)
        assertTrue(METADATA_FORMAT.contains("{{status}}"))
        assertTrue(METADATA_FORMAT.contains("{{mpris:artUrl}}"))
        assertTrue(METADATA_FORMAT.contains("{{mpris:length}}"))
        assertTrue(METADATA_FORMAT.contains("{{position}}"))
    }

    @Test
    fun `control commands target the chosen player`() {
        assertEquals(listOf("playerctl", "--player=mpd", "play-pause"), controlCommand("mpd", ACTION_PLAY_PAUSE))
        assertEquals(listOf("playerctl", "next"), controlCommand(null, ACTION_NEXT))
        assertEquals(listOf("playerctl", "previous"), controlCommand(null, ACTION_PREVIOUS))
    }

    @Test
    fun `seeking converts milliseconds to seconds`() {
        assertEquals(listOf("playerctl", "position", "42.500"), seekCommand(null, 42_500L))
        assertEquals("0.000", seconds(0L))
        assertEquals("12.345", seconds(12_345L))
    }

    @Test
    fun `playerctl lists one player per line`() {
        assertEquals(listOf("spotify", "mpd"), parsePlayers("spotify\n\nmpd\n"))
        assertEquals(emptyList<String>(), parsePlayers(""))
    }

    @Test
    fun `metadata is parsed into a track`() {
        val raw = "Playing${sep}Song${sep}Artist${sep}Album${sep}file:///cover.png${sep}180000000${sep}42000000"
        val track = parseMetadata(raw, PlayerctlProvider.ID)
        assertTrue(track != null)
        track!!
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals("file:///cover.png", track.artworkUrl)
        assertEquals(180_000L, track.durationMs)
        assertEquals(42_000L, track.positionMs)
        assertTrue(track.isPlaying)
        assertEquals("Song - Artist", track.displayLabel)
        assertEquals("playerctl", track.providerId)
    }

    @Test
    fun `a paused player with unknown metadata still maps`() {
        val track = parseMetadata("Paused${sep}Song$sep$sep$sep$sep$sep", PlayerctlProvider.ID)
        assertTrue(track != null)
        track!!
        assertFalse(track.isPlaying)
        assertNull(track.artist)
        assertNull(track.album)
        assertNull(track.artworkUrl)
        assertNull(track.durationMs)
        assertEquals("Song", track.displayLabel)
    }

    @Test
    fun `unexpected playerctl output is reported as nothing playing`() {
        assertNull(parseMetadata("", PlayerctlProvider.ID))
        assertNull(parseMetadata("No player could handle this command", PlayerctlProvider.ID))
        assertNull(parseMetadata("Playing${sep}${sep}Artist$sep$sep$sep$sep", PlayerctlProvider.ID))
    }

    @Test
    fun `microseconds and seconds both convert to milliseconds`() {
        assertEquals(1_500L, microsToMs("1500000"))
        assertEquals(0L, microsToMs("0"))
        assertNull(microsToMs(""))
        assertNull(microsToMs("n/a"))
        assertEquals(1_234L, secondsToMs("1.234"))
        assertNull(secondsToMs("n/a"))
    }

    @Test
    fun `nothing is polled when playerctl is not installed`() {
        val runner = RecordingProcessRunner { null }
        val provider = PlayerctlProvider(runner, configuredPlayer = { null })
        assumeTrue(SystemInfo.isLinux)
        assertFalse(provider.isConfigured())
        assertNull(provider.fetchState())
        assertEquals(listOf(listOf("playerctl", "--version")), runner.commands)
    }

    @Test
    fun `the first player is polled when none is configured`() {
        assumeTrue(SystemInfo.isLinux)
        val runner = RecordingProcessRunner { command ->
            when {
                command.size == 2 && command[1] == "--version" -> ProcessResult(0, "playerctl 2.4.1")
                command.size == 2 && command[1] == "--list-all" -> ProcessResult(0, "spotify\nmpd\n")
                else -> ProcessResult(0, "Playing${sep}Song${sep}Artist$sep$sep$sep$sep")
            }
        }
        val provider = PlayerctlProvider(runner, configuredPlayer = { null })
        assertEquals("Song", provider.fetchState()?.title)
        assertEquals(
            listOf("playerctl", "--player=spotify", "metadata", "--format", METADATA_FORMAT),
            runner.commands.last()
        )
    }

    @Test
    fun `the configured player is used and controls are sent to it`() {
        assumeTrue(SystemInfo.isLinux)
        val runner = RecordingProcessRunner { command ->
            when {
                command.size == 2 && command[1] == "--version" -> ProcessResult(0, "playerctl 2.4.1")
                else -> ProcessResult(0, "Playing${sep}Song$sep$sep$sep$sep$sep")
            }
        }
        val provider = PlayerctlProvider(runner, configuredPlayer = { "mpd" })
        provider.playPause()
        provider.next()
        provider.previous()
        provider.seek(30_000L)
        assertEquals(
            listOf(
                listOf("playerctl", "--player=mpd", "play-pause"),
                listOf("playerctl", "--player=mpd", "next"),
                listOf("playerctl", "--player=mpd", "previous"),
                listOf("playerctl", "--player=mpd", "position", "30.000"),
            ),
            runner.commands.filter { it.last() != "--version" }
        )
    }
}
