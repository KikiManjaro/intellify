package com.github.kikimanjaro.intellify.provider.process

/**
 * [ProcessRunner] for the tests: records every command and answers from [respond].
 */
class RecordingProcessRunner(
    private val respond: (List<String>) -> ProcessResult?,
) : ProcessRunner {

    val commands = mutableListOf<List<String>>()

    override fun run(command: List<String>, timeoutMs: Long): ProcessResult? {
        commands.add(command)
        return respond(command)
    }
}
