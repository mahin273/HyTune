package com.mahin.hytune.core.privilege

data class CommandResult(
    val exitCode: Int,
    val stdout: List<String> = emptyList(),
    val stderr: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = exitCode == 0

    val output: String get() = stdout.joinToString("\n")

    val errorOutput: String get() = stderr.joinToString("\n")

    companion object {
        fun success(outputLines: List<String> = emptyList()): CommandResult {
            return CommandResult(exitCode = 0, stdout = outputLines, stderr = emptyList())
        }

        fun failure(exitCode: Int, errorLines: List<String> = emptyList()): CommandResult {
            require(exitCode != 0) { "Failure exit code must not be 0" }
            return CommandResult(exitCode = exitCode, stdout = emptyList(), stderr = errorLines)
        }
    }
}
