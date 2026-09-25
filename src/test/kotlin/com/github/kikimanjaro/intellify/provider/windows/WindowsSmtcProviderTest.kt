package com.github.kikimanjaro.intellify.provider.windows

import com.github.kikimanjaro.intellify.provider.process.ProcessResult
import com.github.kikimanjaro.intellify.provider.process.RecordingProcessRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WindowsSmtcProviderTest {

    @Test
    fun `the PowerShell command is non-interactive and passes the command through`() {
        assertEquals(
            listOf(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                "C:\\Temp\\nowplaying.ps1",
                NOWPLAYING_STATUS,
            ),
            powerShellCommand("C:\\Temp\\nowplaying.ps1", NOWPLAYING_STATUS)
        )
    }

    @Test
    fun `the JSON line is mapped to a track`() {
        val raw =
            """{"status":"Playing","title":"Song","artist":"Artist","album":"Album","durationMs":180000,"positionMs":42000}"""
        val track = parseNowPlaying(raw, WindowsSmtcProvider.ID)
        assertTrue(track != null)
        track!!
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals(180_000L, track.durationMs)
        assertEquals(42_000L, track.positionMs)
        assertTrue(track.isPlaying)
        assertEquals("Song - Artist", track.displayLabel)
    }

    @Test
    fun `no session, no title and garbage all map to nothing playing`() {
        assertNull(parseNowPlaying("""{"status":"None"}""", WindowsSmtcProvider.ID))
        assertNull(parseNowPlaying("""{"status":"Playing"}""", WindowsSmtcProvider.ID))
        assertNull(parseNowPlaying("""{"status":"Playing","title":"   "}""", WindowsSmtcProvider.ID))
        assertNull(parseNowPlaying("", WindowsSmtcProvider.ID))
        assertNull(parseNowPlaying("not json at all", WindowsSmtcProvider.ID))
    }

    @Test
    fun `a paused session without timeline data still maps`() {
        val track = parseNowPlaying("""{"status":"Paused","title":"Song"}""", WindowsSmtcProvider.ID)
        assertTrue(track != null)
        track!!
        assertFalse(track.isPlaying)
        assertNull(track.artist)
        assertNull(track.durationMs)
        assertNull(track.positionMs)
    }

    @Test
    fun `the provider does nothing when the script is unavailable`() {
        val runner = RecordingProcessRunner { ProcessResult(0, """{"status":"Playing","title":"Song"}""") }
        val provider = WindowsSmtcProvider(runner, scriptPath = { null })
        assertFalse(provider.isConfigured())
        assertNull(provider.fetchState())
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun `polling runs the bundled script with the status command`() {
        val runner = RecordingProcessRunner { ProcessResult(0, """{"status":"Playing","title":"Song"}""") }
        val provider = WindowsSmtcProvider(runner, scriptPath = { "C:\\Temp\\nowplaying.ps1" })
        if (!com.intellij.openapi.util.SystemInfo.isWindows) {
            // isConfigured() is platform-gated, so on Linux/macOS nothing must run at all.
            assertNull(provider.fetchState())
            return
        }
        assertEquals("Song", provider.fetchState()?.title)
    }

    @Test
    fun `the bundled PowerShell script ships with the plugin`() {
        val resource = WindowsSmtcProvider::class.java.getResource(WindowsSmtcProvider.SCRIPT_RESOURCE)
        assertNotNull("missing resource " + WindowsSmtcProvider.SCRIPT_RESOURCE, resource)
    }

    @Test
    fun `the bundled script is extracted to a file PowerShell can run`() {
        val path = NowPlayingScript.path()
        assertNotNull(path)
        val file = File(path!!)
        assertTrue(file.isFile)
        assertTrue(file.readText().contains("GlobalSystemMediaTransportControlsSessionManager"))
        assertEquals(path, NowPlayingScript.path())
    }
}
