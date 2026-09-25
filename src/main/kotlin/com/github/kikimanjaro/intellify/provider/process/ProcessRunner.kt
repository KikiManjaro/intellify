package com.github.kikimanjaro.intellify.provider.process

import java.util.concurrent.TimeUnit

/** What a finished command returned (stdout and stderr merged). */
data class ProcessResult(val exitCode: Int, val stdout: String)

/**
 * Runs an external command.
 *
 * Every system provider (playerctl, PowerShell, osascript) goes through this interface: it keeps
 * the command building and the output parsing in pure functions that can be unit tested, while the
 * sub-process part stays behind one seam.
 */
interface ProcessRunner {
    /** Runs [command] and returns its merged output, or `null` when it cannot be started. */
    fun run(command: List<String>, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ProcessResult?

    companion object {
        /** Hard timeout: a stuck player must never freeze the status bar updater. */
        const val DEFAULT_TIMEOUT_MS: Long = 3_000L
    }
}

/**
 * [ProcessRunner] backed by [ProcessBuilder] — no JNA, no new dependency.
 *
 * stderr is merged into stdout, the wait is bounded by `timeoutMs`, and the process is killed when
 * the timeout expires. An absent binary surfaces as `null` (`IOException` on start).
 */
object SystemProcessRunner : ProcessRunner {
    override fun run(command: List<String>, timeoutMs: Long): ProcessResult? {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            // IOException: the command does not exist on this machine.
            return null
        }

        // Read continuously: the child must never block on a full pipe while we wait for it.
        val output = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().use { buffered ->
                    buffered.readLines().forEach { output.append(it).append('\n') }
                }
            } catch (_: Exception) {
                // The process was killed while reading; keep whatever was captured.
            }
        }.apply {
            isDaemon = true
            start()
        }

        return try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                null
            } else {
                reader.join(500L)
                ProcessResult(process.exitValue(), output.toString())
            }
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            null
        }
    }
}
