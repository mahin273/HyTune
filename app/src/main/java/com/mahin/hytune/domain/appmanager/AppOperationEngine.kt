package com.mahin.hytune.domain.appmanager

import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.appmanager.model.AppOperation
import com.mahin.hytune.domain.appmanager.model.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOperationEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val safetyGuard: PackageSafetyGuard,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun executeOperation(
        packageName: String,
        operation: AppOperation,
        keepData: Boolean = false
    ): Result<OperationResult> = withContext(ioDispatcher) {
        runCatching {
            val targetPkg = packageName.trim()
            safetyGuard.validateOperation(targetPkg, operation.isDestructive)

            if (!privilegeBackend.hasCapability(Capability.APP_FREEZE)) {
                throw SecurityException("Active backend (${privilegeBackend.tier}) does not support APP_FREEZE capability")
            }

            val command = buildCommand(targetPkg, operation, keepData)
            val result = privilegeBackend.exec(command)

            val output = result.output
            val error = result.errorOutput

            val hasErrorKeywords = output.contains("Failure", ignoreCase = true) ||
                    output.contains("Error:", ignoreCase = true) ||
                    output.contains("DELETE_FAILED", ignoreCase = true)

            if (result.isSuccess && !hasErrorKeywords) {
                OperationResult(
                    packageName = targetPkg,
                    operation = operation,
                    success = true,
                    message = output.ifBlank { "Operation completed successfully" },
                    commandExecuted = command
                )
            } else {
                val failureMessage = error.ifBlank {
                    output.ifBlank { "Command failed with exit code ${result.exitCode}" }
                }
                OperationResult(
                    packageName = targetPkg,
                    operation = operation,
                    success = false,
                    message = failureMessage,
                    commandExecuted = command
                )
            }
        }
    }

    private fun buildCommand(packageName: String, operation: AppOperation, keepData: Boolean): String {
        return when (operation) {
            AppOperation.FREEZE -> "pm disable-user --user 0 $packageName"
            AppOperation.UNFREEZE -> "pm enable $packageName"
            AppOperation.SUSPEND -> "pm suspend --user 0 $packageName"
            AppOperation.UNSUSPEND -> "pm unsuspend --user 0 $packageName"
            AppOperation.HIDE -> "pm hide --user 0 $packageName"
            AppOperation.UNHIDE -> "pm unhide --user 0 $packageName"
            AppOperation.FORCE_STOP -> "am force-stop $packageName"
            AppOperation.CLEAR_DATA -> "pm clear --user 0 $packageName"
            AppOperation.UNINSTALL_USER_0 -> {
                if (keepData) {
                    "pm uninstall -k --user 0 $packageName"
                } else {
                    "pm uninstall --user 0 $packageName"
                }
            }
            AppOperation.REINSTALL_EXISTING -> "cmd package install-existing $packageName"
        }
    }

    suspend fun freeze(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.FREEZE)

    suspend fun unfreeze(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.UNFREEZE)

    suspend fun suspend(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.SUSPEND)

    suspend fun unsuspend(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.UNSUSPEND)

    suspend fun hide(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.HIDE)

    suspend fun unhide(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.UNHIDE)

    suspend fun forceStop(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.FORCE_STOP)

    suspend fun clearData(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.CLEAR_DATA)

    suspend fun uninstallUser0(packageName: String, keepData: Boolean = false): Result<OperationResult> =
        executeOperation(packageName, AppOperation.UNINSTALL_USER_0, keepData)

    suspend fun reinstallExisting(packageName: String): Result<OperationResult> =
        executeOperation(packageName, AppOperation.REINSTALL_EXISTING)
}
