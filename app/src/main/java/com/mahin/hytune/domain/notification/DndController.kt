package com.mahin.hytune.domain.notification

import com.mahin.hytune.core.privilege.PrivilegeManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DndController @Inject constructor(
    private val privilegeManager: PrivilegeManager
) {

    suspend fun getCurrentZenMode(): String? {
        val backend = privilegeManager.activeBackend.value
        val result = backend.exec("settings get global zen_mode")
        if (result.isSuccess) {
            val trimmed = result.output.trim()
            if (trimmed.isNotEmpty() && trimmed != "null") {
                return trimmed
            }
        }
        return null
    }

    suspend fun enableDnd(): Boolean {
        val backend = privilegeManager.activeBackend.value
        val result = backend.exec("settings put global zen_mode 1")
        return result.isSuccess
    }

    suspend fun restoreDnd(originalZenMode: String?): Boolean {
        val backend = privilegeManager.activeBackend.value
        val target = originalZenMode ?: "0"
        val result = backend.exec("settings put global zen_mode $target")
        return result.isSuccess
    }
}
