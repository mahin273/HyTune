package com.mahin.hytune.domain.process

import com.mahin.hytune.domain.appmanager.PackageSafetyGuard
import com.mahin.hytune.domain.process.model.ProcessInfo
import com.mahin.hytune.domain.process.model.ProcessSignal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessRepository @Inject constructor(
    private val processEngine: ProcessEngine,
    private val safetyGuard: PackageSafetyGuard,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val criticalNativeProcesses = setOf(
        "init",
        "system_server",
        "zygote",
        "zygote64",
        "surfaceflinger",
        "servicemanager",
        "hwservicemanager",
        "audioserver",
        "vold",
        "ueventd",
        "adbd"
    )

    fun isProcessCritical(name: String, packageName: String?): Boolean {
        if (criticalNativeProcesses.contains(name.lowercase())) {
            return true
        }
        if (packageName != null && safetyGuard.isCritical(packageName)) {
            return true
        }
        return safetyGuard.isCritical(name)
    }

    suspend fun getProcesses(): List<ProcessInfo> = withContext(ioDispatcher) {
        val rawList = processEngine.getProcesses()
        rawList.map { process ->
            val critical = isProcessCritical(process.name, process.packageName)
            process.copy(isCritical = critical)
        }
    }

    suspend fun sendSignal(pid: Int, processName: String, signal: ProcessSignal): Result<String> = withContext(ioDispatcher) {
        if (isProcessCritical(processName, null) && (signal == ProcessSignal.KILL || signal == ProcessSignal.STOP || signal == ProcessSignal.TERM)) {
            return@withContext Result.failure(
                SecurityException("Cannot send ${signal.signalName} to protected system process: $processName")
            )
        }

        val result = processEngine.sendSignal(pid, signal)
        result.map { "Sent ${signal.signalName} to PID $pid ($processName)" }
    }

    suspend fun setNice(pid: Int, processName: String, nice: Int): Result<String> = withContext(ioDispatcher) {
        val result = processEngine.setNice(pid, nice)
        result.map { "Set nice level to $nice for PID $pid ($processName)" }
    }

    suspend fun setOomScoreAdj(pid: Int, processName: String, adj: Int): Result<String> = withContext(ioDispatcher) {
        if (isProcessCritical(processName, null) && adj > 0) {
            return@withContext Result.failure(
                SecurityException("Cannot deprioritize OOM score for protected process: $processName")
            )
        }

        val result = processEngine.setOomScoreAdj(pid, adj)
        result.map { "Set OOM adjustment to $adj for PID $pid ($processName)" }
    }

    suspend fun getOomScoreAdj(pid: Int): Int? = withContext(ioDispatcher) {
        processEngine.getOomScoreAdj(pid)
    }
}
