package com.mahin.hytune.domain.process

import android.app.ActivityManager
import android.content.Context
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.process.model.ProcessInfo
import com.mahin.hytune.domain.process.model.ProcessSignal
import com.mahin.hytune.domain.process.model.ProcessState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getProcesses(): List<ProcessInfo> = withContext(ioDispatcher) {
        if (privilegeBackend.tier == PrivilegeTier.NONE) {
            return@withContext getProcessesFromActivityManager()
        }

        val result = privilegeBackend.exec("ps -A -o pid,ppid,user,ni,vsz,rss,%cpu,args")
        if (result.exitCode != 0 || result.stdout.isEmpty()) {
            // Fallback to simpler ps command if %cpu is unsupported
            val fallbackResult = privilegeBackend.exec("ps -A -o pid,ppid,user,ni,vsz,rss,args")
            if (fallbackResult.exitCode == 0 && fallbackResult.stdout.isNotEmpty()) {
                return@withContext parsePsOutput(fallbackResult.stdout, hasCpuColumn = false)
            }
            return@withContext getProcessesFromActivityManager()
        }

        parsePsOutput(result.stdout, hasCpuColumn = true)
    }

    suspend fun sendSignal(pid: Int, signal: ProcessSignal): Result<Unit> = withContext(ioDispatcher) {
        val cmd = "kill -${signal.code} $pid"
        val result = privilegeBackend.exec(cmd)
        if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val errorMsg = result.errorOutput.ifBlank { "Signal ${signal.signalName} failed for PID $pid" }
            Result.failure(RuntimeException(errorMsg))
        }
    }

    suspend fun setNice(pid: Int, nice: Int): Result<Unit> = withContext(ioDispatcher) {
        val clampedNice = nice.coerceIn(-20, 19)
        val cmd = "renice -n $clampedNice -p $pid"
        val result = privilegeBackend.exec(cmd)
        if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val errorMsg = result.errorOutput.ifBlank { "Failed to set nice level for PID $pid" }
            Result.failure(RuntimeException(errorMsg))
        }
    }

    suspend fun setOomScoreAdj(pid: Int, oomScoreAdj: Int): Result<Unit> = withContext(ioDispatcher) {
        val clampedAdj = oomScoreAdj.coerceIn(-1000, 1000)
        val path = "/proc/$pid/oom_score_adj"
        val writeResult = privilegeBackend.writeFile(path, clampedAdj.toString())
        if (writeResult.isSuccess) {
            return@withContext Result.success(Unit)
        }

        // Fallback to shell echo
        val cmd = "echo $clampedAdj > $path"
        val result = privilegeBackend.exec(cmd)
        if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val errorMsg = result.errorOutput.ifBlank { "Failed to set oom_score_adj for PID $pid" }
            Result.failure(RuntimeException(errorMsg))
        }
    }

    suspend fun getOomScoreAdj(pid: Int): Int? = withContext(ioDispatcher) {
        val path = "/proc/$pid/oom_score_adj"
        val sysfsContent = privilegeBackend.readFile(path).getOrNull()
        sysfsContent?.trim()?.toIntOrNull()?.let { return@withContext it }

        val result = privilegeBackend.exec("cat $path")
        if (result.isSuccess && result.stdout.isNotEmpty()) {
            result.stdout.firstOrNull()?.trim()?.toIntOrNull()
        } else {
            null
        }
    }

    private fun parsePsOutput(lines: List<String>, hasCpuColumn: Boolean): List<ProcessInfo> {
        val processes = mutableListOf<ProcessInfo>()
        if (lines.isEmpty()) return processes

        // Skip header line if present
        val dataLines = if (lines.first().contains("PID", ignoreCase = true)) {
            lines.drop(1)
        } else {
            lines
        }

        for (rawLine in dataLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val tokens = line.split("\\s+".toRegex())
            val minTokens = if (hasCpuColumn) 8 else 7
            if (tokens.size < minTokens) continue

            val pid = tokens[0].toIntOrNull() ?: continue
            val ppid = tokens[1].toIntOrNull() ?: 0
            val user = tokens[2]
            val nice = tokens[3].toIntOrNull() ?: 0
            val vsz = tokens[4].toLongOrNull() ?: 0L
            val rss = tokens[5].toLongOrNull() ?: 0L

            val (cpuPercent, cmdTokensStartIndex) = if (hasCpuColumn) {
                val cpu = tokens[6].replace("%", "").toFloatOrNull() ?: 0.0f
                Pair(cpu, 7)
            } else {
                Pair(0.0f, 6)
            }

            val rawCmd = tokens.subList(cmdTokensStartIndex, tokens.size).joinToString(" ")
            val cleanCmd = rawCmd.trim()
            if (cleanCmd.isEmpty()) continue

            val name = cleanCmd.substringAfterLast("/").substringBefore(" ")
            val packageName = extractPackageName(cleanCmd)

            processes.add(
                ProcessInfo(
                    pid = pid,
                    ppid = ppid,
                    name = if (name.isNotBlank()) name else cleanCmd,
                    user = user,
                    packageName = packageName,
                    cpuUsagePercent = cpuPercent,
                    rssKb = rss,
                    vsizeKb = vsz,
                    nice = nice,
                    oomScoreAdj = null,
                    state = ProcessState.RUNNING
                )
            )
        }

        return processes
    }

    private fun extractPackageName(cmd: String): String? {
        val base = cmd.substringBefore(" ").substringAfterLast("/")
        val cleaned = base.substringBefore(":")
        // Typical Android package name pattern: contains at least one dot and lowercase alphanumeric
        if (cleaned.contains(".") && cleaned.matches("^[a-zA-Z0-9_.]+$".toRegex())) {
            return cleaned
        }
        return null
    }

    private fun getProcessesFromActivityManager(): List<ProcessInfo> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return emptyList()

        val runningProcesses = activityManager.runningAppProcesses ?: return emptyList()
        val pids = runningProcesses.map { it.pid }.toIntArray()
        val memoryInfos = if (pids.isNotEmpty()) {
            activityManager.getProcessMemoryInfo(pids)
        } else {
            emptyArray()
        }

        return runningProcesses.mapIndexed { index, processInfo ->
            val rssKb = if (index < memoryInfos.size) {
                memoryInfos[index].totalPss.toLong()
            } else {
                0L
            }

            val pkg = processInfo.pkgList?.firstOrNull() ?: extractPackageName(processInfo.processName)

            ProcessInfo(
                pid = processInfo.pid,
                ppid = 0,
                name = processInfo.processName,
                user = "app",
                packageName = pkg,
                cpuUsagePercent = 0.0f,
                rssKb = rssKb,
                vsizeKb = 0L,
                nice = 0,
                oomScoreAdj = null,
                state = ProcessState.RUNNING
            )
        }
    }
}
