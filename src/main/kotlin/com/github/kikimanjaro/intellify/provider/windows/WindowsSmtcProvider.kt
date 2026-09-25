package com.github.kikimanjaro.intellify.provider.windows

import com.github.kikimanjaro.intellify.provider.MusicProvider
import com.github.kikimanjaro.intellify.provider.ProviderCapability
import com.github.kikimanjaro.intellify.provider.TrackInfo
import com.github.kikimanjaro.intellify.provider.json.MiniJson
import com.github.kikimanjaro.intellify.provider.json.long
import com.github.kikimanjaro.intellify.provider.json.string
import com.github.kikimanjaro.intellify.provider.process.ProcessRunner
import com.github.kikimanjaro.intellify.provider.process.SystemProcessRunner
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files

/** Commands understood by the bundled `nowplaying.ps1`. */
internal const val NOWPLAYING_STATUS = "status"
internal const val NOWPLAYING_PLAY_PAUSE = "playpause"
internal const val NOWPLAYING_NEXT = "next"
internal const val NOWPLAYING_PREVIOUS = "prev"

/** Status emitted by the script when there is no media session at all. */
internal const val STATUS_NONE = "None"

// --- command building (pure) -----------------------------------------------------------------------

internal fun powerShellCommand(scriptPath: String, command: String): List<String> = listOf(
    "powershell",
    "-NoProfile",
    "-NonInteractive",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    scriptPath,
    command,
)

// --- output parsing (pure) -------------------------------------------------------------------------

/**
 * Parses the single JSON line emitted by the script. `null` when there is no session, when nothing
 * is playing, or when the output is garbage.
 */
internal fun parseNowPlaying(raw: String, providerId: String): TrackInfo? {
    val line = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    val json = MiniJson.parseObject(line) ?: return null

    val status = json.string("status") ?: return null
    if (status.equals(STATUS_NONE, ignoreCase = true)) return null

    val title = json.string("title") ?: return null
    return TrackInfo(
        title = title,
        artist = json.string("artist"),
        album = json.string("album"),
        artworkUrl = null,
        durationMs = json.long("durationMs")?.takeIf { it > 0L },
        positionMs = json.long("positionMs")?.takeIf { it >= 0L },
        isPlaying = status.equals("Playing", ignoreCase = true),
        providerId = providerId,
    )
}

/**
 * Windows provider: a bundled PowerShell script drives the WinRT
 * `GlobalSystemMediaTransportControlsSessionManager` and prints one JSON line.
 *
 * @param runner seam for the tests; the real one is [SystemProcessRunner].
 * @param scriptPath path of the extracted script; `null` when it could not be extracted.
 */
class WindowsSmtcProvider(
    private val runner: ProcessRunner = SystemProcessRunner,
    private val scriptPath: () -> String? = { NowPlayingScript.path() },
) : MusicProvider {

    override val id: String = ID
    override val displayName: String = "Windows media (SMTC)"
    override val capabilities: Set<ProviderCapability> = setOf(ProviderCapability.CONTROL)

    override fun isConfigured(): Boolean = SystemInfo.isWindows && scriptPath() != null

    override fun fetchState(): TrackInfo? {
        if (!isConfigured()) return null
        val path = scriptPath() ?: return null
        val result = runner.run(powerShellCommand(path, NOWPLAYING_STATUS)) ?: return null
        if (result.exitCode != 0) return null
        return parseNowPlaying(result.stdout, id)
    }

    override fun playPause() = control(NOWPLAYING_PLAY_PAUSE)

    override fun next() = control(NOWPLAYING_NEXT)

    override fun previous() = control(NOWPLAYING_PREVIOUS)

    override fun describeStatus(): String = when {
        !SystemInfo.isWindows -> "Windows media sessions are only available on Windows"
        scriptPath() == null -> "the bundled PowerShell script ($SCRIPT_RESOURCE) could not be extracted"
        else -> "the System Media Transport Controls session of Windows is used through PowerShell"
    }

    private fun control(command: String) {
        val path = scriptPath() ?: return
        runner.run(powerShellCommand(path, command))
    }

    companion object {
        const val ID: String = "windows-smtc"

        /** Resource holding the PowerShell bridge, shipped inside the plugin jar. */
        const val SCRIPT_RESOURCE: String = "/providers/nowplaying.ps1"
    }
}

/**
 * Extracts [WindowsSmtcProvider.SCRIPT_RESOURCE] to a temporary `.ps1` file: PowerShell can only run
 * a script from the file system, not from inside the plugin jar. Extracted once per session.
 */
internal object NowPlayingScript {
    @Volatile
    private var extracted: String? = null

    fun path(): String? = extracted ?: extract()?.also { extracted = it }

    private fun extract(): String? = try {
        val resource = WindowsSmtcProvider::class.java.getResourceAsStream(WindowsSmtcProvider.SCRIPT_RESOURCE)
        if (resource == null) {
            null
        } else {
            val file = Files.createTempFile("intellify-nowplaying", ".ps1").toFile()
            file.deleteOnExit()
            resource.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}
