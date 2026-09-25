package com.github.kikimanjaro.intellify.provider.playerctl

import com.github.kikimanjaro.intellify.provider.MusicProvider
import com.github.kikimanjaro.intellify.provider.ProviderCapability
import com.github.kikimanjaro.intellify.provider.TrackInfo
import com.github.kikimanjaro.intellify.provider.process.ProcessRunner
import com.github.kikimanjaro.intellify.provider.process.SystemProcessRunner
import com.github.kikimanjaro.intellify.settings.IntellifySettings
import com.intellij.openapi.util.SystemInfo
import java.util.Locale

/**
 * Playerctl is a command line controller for MPRIS players (Linux). "unit separator" is used as the
 * field separator: it cannot appear in metadata, unlike `|`, `;` or a tab.
 */
internal const val FIELD_SEPARATOR = "\u001F"

/** Metadata template: one line, `\u001F`-separated, in the order [parseMetadata] expects. */
internal const val METADATA_FORMAT =
    "{{status}}$FIELD_SEPARATOR{{title}}$FIELD_SEPARATOR{{artist}}$FIELD_SEPARATOR{{album}}" +
        "$FIELD_SEPARATOR{{mpris:artUrl}}$FIELD_SEPARATOR{{mpris:length}}$FIELD_SEPARATOR{{position}}"

internal const val ACTION_PLAY_PAUSE = "play-pause"
internal const val ACTION_NEXT = "next"
internal const val ACTION_PREVIOUS = "previous"

private const val PLAYERCTL = "playerctl"

// --- command building (pure) -----------------------------------------------------------------------

private fun playerArguments(player: String?): List<String> =
    if (player.isNullOrBlank()) listOf(PLAYERCTL) else listOf(PLAYERCTL, "--player=$player")

internal fun listPlayersCommand(): List<String> = listOf(PLAYERCTL, "--list-all")

internal fun metadataCommand(player: String?): List<String> =
    playerArguments(player) + listOf("metadata", "--format", METADATA_FORMAT)

internal fun controlCommand(player: String?, action: String): List<String> =
    playerArguments(player) + listOf(action)

internal fun seekCommand(player: String?, positionMs: Long): List<String> =
    playerArguments(player) + listOf("position", seconds(positionMs))

/** `playerctl position <seconds>`: seconds with a decimal part, locale-independent. */
internal fun seconds(positionMs: Long): String =
    String.format(Locale.ROOT, "%.3f", positionMs / 1000.0)

// --- output parsing (pure) -------------------------------------------------------------------------

internal fun parsePlayers(raw: String): List<String> =
    raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** MPRIS exposes lengths and positions in microseconds. */
internal fun microsToMs(raw: String): Long? =
    raw.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { (it / 1000.0).toLong() }

/** `playerctl position` prints seconds with a decimal part. */
internal fun secondsToMs(raw: String): Long? =
    raw.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { (it * 1000.0).toLong() }

/**
 * Parses the `--format` line produced by [metadataCommand]. Returns `null` when playerctl printed
 * something unexpected (unknown player, player gone, error message).
 */
internal fun parseMetadata(raw: String, providerId: String): TrackInfo? {
    // No trim() here: the field separator is \u001F and Java's trim() strips every character <= 32,
    // which would silently drop the trailing (empty) fields. Each field is trimmed individually below.
    val line = raw.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
    val fields = line.split(FIELD_SEPARATOR)
    if (fields.size < 7) return null

    val title = fields[1].trim()
    if (title.isEmpty()) return null

    return TrackInfo(
        title = title,
        artist = fields[2].trim().ifBlank { null },
        album = fields[3].trim().ifBlank { null },
        artworkUrl = fields[4].trim().ifBlank { null },
        durationMs = microsToMs(fields[5]),
        positionMs = microsToMs(fields[6]),
        isPlaying = fields[0].trim().equals("Playing", ignoreCase = true),
        providerId = providerId,
    )
}

/**
 * Linux / MPRIS provider: `playerctl` is spawned through [ProcessRunner] for every call.
 *
 * @param runner seam for the tests; the real one is [SystemProcessRunner].
 * @param configuredPlayer player name from the settings; empty means "use the first player listed".
 */
class PlayerctlProvider(
    private val runner: ProcessRunner = SystemProcessRunner,
    private val configuredPlayer: () -> String? = { IntellifySettings.getInstance()?.state?.playerctlPlayer },
) : MusicProvider {

    override val id: String = ID
    override val displayName: String = "playerctl (MPRIS, Linux)"
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.CONTROL,
        ProviderCapability.POSITION,
        ProviderCapability.ARTWORK,
    )

    @Volatile
    private var available: Boolean? = null

    override fun isConfigured(): Boolean = SystemInfo.isLinux && playerctlAvailable()

    override fun fetchState(): TrackInfo? {
        if (!isConfigured()) return null
        val player = resolvePlayer() ?: return null
        val result = runner.run(metadataCommand(player)) ?: return null
        if (result.exitCode != 0) return null
        return parseMetadata(result.stdout, id)
    }

    override fun playPause() = control(ACTION_PLAY_PAUSE)

    override fun next() = control(ACTION_NEXT)

    override fun previous() = control(ACTION_PREVIOUS)

    override fun seek(positionMs: Long) {
        val player = resolvePlayer() ?: return
        runner.run(seekCommand(player, positionMs))
    }

    override fun describeStatus(): String = when {
        !SystemInfo.isLinux -> "playerctl is only available on Linux"
        !playerctlAvailable() -> "playerctl is not installed (or not on the PATH)"
        else -> {
            val players = listPlayers()
            if (players.isEmpty()) {
                "playerctl is installed, but no MPRIS player is running"
            } else {
                "playerctl is installed, players: " + players.joinToString(", ")
            }
        }
    }

    private fun playerctlAvailable(): Boolean = available
        ?: (runner.run(listOf(PLAYERCTL, "--version"), 2_000L)?.exitCode == 0).also { available = it }

    internal fun listPlayers(): List<String> =
        runner.run(listPlayersCommand())
            ?.takeIf { it.exitCode == 0 }
            ?.let { parsePlayers(it.stdout) }
            .orEmpty()

    /** The player from the settings when it is set, the first one reported by playerctl otherwise. */
    internal fun resolvePlayer(): String? {
        val configured = configuredPlayer()
        if (!configured.isNullOrBlank()) return configured
        return listPlayers().firstOrNull()
    }

    private fun control(action: String) {
        val player = resolvePlayer() ?: return
        runner.run(controlCommand(player, action))
    }

    companion object {
        const val ID: String = "playerctl"
    }
}
