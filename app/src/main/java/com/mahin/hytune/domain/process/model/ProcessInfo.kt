package com.mahin.hytune.domain.process.model

data class ProcessInfo(
    val pid: Int,
    val ppid: Int = 0,
    val name: String,
    val user: String = "",
    val packageName: String? = null,
    val cpuUsagePercent: Float = 0.0f,
    val rssKb: Long = 0L,
    val vsizeKb: Long = 0L,
    val nice: Int = 0,
    val oomScoreAdj: Int? = null,
    val state: ProcessState = ProcessState.UNKNOWN,
    val isCritical: Boolean = false
) {
    val rssFormatted: String
        get() {
            val mb = rssKb / 1024.0
            return if (mb >= 1024.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }

    val cpuFormatted: String
        get() = String.format("%.1f%%", cpuUsagePercent)
}

enum class ProcessState {
    RUNNING,
    SLEEPING,
    DISK_SLEEP,
    STOPPED,
    ZOMBIE,
    UNKNOWN
}

enum class ProcessSignal(val code: Int, val signalName: String) {
    KILL(9, "SIGKILL"),
    TERM(15, "SIGTERM"),
    STOP(19, "SIGSTOP"),
    CONT(18, "SIGCONT")
}

enum class ProcessSortBy {
    CPU,
    RAM,
    PID,
    NAME
}

enum class ProcessFilter {
    ALL,
    USER_APPS,
    SYSTEM
}
