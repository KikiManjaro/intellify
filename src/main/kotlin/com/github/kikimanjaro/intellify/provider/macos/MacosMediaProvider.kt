package com.github.kikimanjaro.intellify.provider.macos

import com.github.kikimanjaro.intellify.provider.MusicProvider
import com.github.kikimanjaro.intellify.provider.ProviderCapability
import com.github.kikimanjaro.intellify.provider.TrackInfo
import com.github.kikimanjaro.intellify.provider.process.ProcessRunner
import com.github.kikimanjaro.intellify.provider.process.SystemProcessRunner
import com.intellij.openapi.util.SystemInfo
import java.util.Locale

internal const val SPOTIFY_APP = "Spotify"
internal const val MUSIC_APP = "Music"

/** Same trick as the Linux provider: a separator that cannot appear in a track title. */
internal const val MAC_FIELD_SEPARATOR = "\u001F"

/** AppleScript reports the player state as `playing` / `paused` / `stopped`. */
private const val PLAYING = "playing"

internal const val ACTION_PLAY_PAUSE = "playpause"
internal const val ACTION_NEXT = "next"
internal const val ACTION_PREVIOUS = "previous"

// --- command building (pure) -----------------------------------------------------------------------

internal fun osascriptCommand(script: String): List<String> = listOf("osascript", "-e", script)

/** Returns which supported player is running, or an empty string. */
internal fun activeAppScript(): String = """
    if application "$SPOTIFY_APP" is running then
        return "$SPOTIFY_APP"
    else if application "$MUSIC_APP" is running then
        return "$MUSIC_APP"
    else
        return ""
    end if
""".trimIndent()

/**
 * One AppleScript call returning everything at once:
 * `state<sep>title<sep>artist<sep>album<sep>duration<sep>position`.
 *
 * `ASCII character 31` is used instead of an escaped `\u001F`: AppleScript source read by
 * `osascript` does not survive raw control characters.
 */
internal fun stateScript(app: String): String = """
    tell application "$app"
        set st to player state as string
        set t to name of current track
        set ar to artist of current track
        set al to album of current track
        set du to duration of current track
        set po to player position
    end tell
    return st & (ASCII character 31) & t & (ASCII character 31) & ar & (ASCII character 31) & al & (ASCII character 31) & (du as string) & (ASCII character 31) & (po as string)
""".trimIndent()

internal fun controlScript(app: String, action: String): String? = when (action) {
    ACTION_PLAY_PAUSE -> "tell application \"$app\" to playpause"
    ACTION_NEXT -> "tell application \"$app\" to next track"
    ACTION_PREVIOUS -> "tell application \"$app\" to previous track"
    else -> null
}

internal fun seekScript(app: String, positionMs: Long): String {
    val seconds = String.format(Locale.ROOT, "%.3f", positionMs / 1000.0)
    return "tell application \"$app\" to set player position to $seconds"
}

// --- output parsing (pure) -------------------------------------------------------------------------

internal fun parseActiveApp(raw: String): String? =
    raw.trim().takeIf { it == SPOTIFY_APP || it == MUSIC_APP }

/**
 * Parses the [stateScript] line. Returns `null` when nothing is playing or the output is garbage.
 *
 * Units differ per application and per field, which is why this stays a pure, tested function:
 * Spotify reports the track `duration` in milliseconds, Music.app in seconds, and both report
 * `player position` in seconds. AppleScript prints reals with the system locale, so a comma
 * decimal separator is accepted as well.
 */
internal fun parseState(raw: String, app: String, providerId: String): TrackInfo? {
    // trim('\r', '\n') and not trim(): trim() strips every character <= 32, which includes the
    // \u001F field separator, so trailing empty fields would disappear.
    val fields = raw.trim('\r', '\n').split(MAC_FIELD_SEPARATOR)
    if (fields.size < 6) return null

    // `equals(ignoreCase = true)` rather than `lowercase()`/`lowercase(Locale)`: those still need
    // @OptIn(ExperimentalStdlibApi::class) with this Kotlin version.
    val isPlaying = fields[0].trim().equals(PLAYING, ignoreCase = true)
    val title = fields[1].trim()
    if (title.isEmpty()) return null

    val toSeconds = { raw: String -> raw.trim().replace(',', '.').toDoubleOrNull() }
    val durationMs = toSeconds(fields[4])?.let { if (app == SPOTIFY_APP) it.toLong() else (it * 1000.0).toLong() }
    val positionMs = toSeconds(fields[5])?.let { (it * 1000.0).toLong() }

    return TrackInfo(
        title = title,
        artist = fields[2].trim().ifBlank { null },
        album = fields[3].trim().ifBlank { null },
        artworkUrl = null,
        durationMs = durationMs?.takeIf { it > 0L },
        positionMs = positionMs?.takeIf { it >= 0L },
        isPlaying = isPlaying,
        providerId = providerId,
    )
}

/**
 * macOS provider: `osascript` drives Spotify.app or Music.app.
 *
 * @param runner seam for the tests; the real one is [SystemProcessRunner].
 */
class MacosMediaProvider(
    private val runner: ProcessRunner = SystemProcessRunner,
) : MusicProvider {

    override val id: String = ID
    override val displayName: String = "macOS media (osascript)"
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.CONTROL,
        ProviderCapability.POSITION,
        ProviderCapability.SEEK,
    )

    override fun isConfigured(): Boolean = SystemInfo.isMac

    override fun fetchState(): TrackInfo? {
        if (!isConfigured()) return null
        val app = runningApp() ?: return null
        val result = runner.run(osascriptCommand(stateScript(app))) ?: return null
        if (result.exitCode != 0) return null
        return parseState(result.stdout, app, id)
    }

    override fun playPause() = control(ACTION_PLAY_PAUSE)

    override fun next() = control(ACTION_NEXT)

    override fun previous() = control(ACTION_PREVIOUS)

    override fun seek(positionMs: Long) {
        val app = runningApp() ?: return
        runner.run(osascriptCommand(seekScript(app, positionMs)))
    }

    override fun describeStatus(): String = when {
        !SystemInfo.isMac -> "osascript is only available on macOS"
        else -> when (val app = runningApp()) {
            null -> "neither Spotify nor Music is running (Spotify.app and Music.app support AppleScript)"
            else -> "$app is running and can be controlled through osascript"
        }
    }

    internal fun runningApp(): String? =
        runner.run(osascriptCommand(activeAppScript()))
            ?.takeIf { it.exitCode == 0 }
            ?.let { parseActiveApp(it.stdout) }

    private fun control(action: String) {
        val app = runningApp() ?: return
        val script = controlScript(app, action) ?: return
        runner.run(osascriptCommand(script))
    }

    companion object {
        const val ID: String = "macos-media"
    }
}
