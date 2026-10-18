package com.mahin.hytune.domain.battery

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.battery.model.ChargeControlStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ChargeControlEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val chargingControlNodes = listOf(
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/battery/battery_charging_enabled",
        "/sys/class/power_supply/battery/input_suspend"
    )

    private val chargeLimitNodes = listOf(
        "/sys/class/power_supply/battery/constant_charge_current_max",
        "/sys/class/power_supply/battery/current_max",
        "/sys/class/power_supply/main/constant_charge_current_max",
        "/sys/class/power_supply/main/current_max"
    )

    suspend fun getStatus(): ChargeControlStatus = withContext(ioDispatcher) {
        val chargingNode = findWorkingChargingNode()
        val limitNode = findWorkingLimitNode()

        if (chargingNode == null && limitNode == null) {
            return@withContext ChargeControlStatus(
                isSupported = false,
                isChargingEnabled = true,
                isBypassActive = false,
                currentLimitMa = 0,
                activeChargingNode = null,
                activeLimitNode = null
            )
        }

        val isChargingEnabled = if (chargingNode != null) {
            val raw = privilegeBackend.readFile(chargingNode).getOrNull()?.trim()
            if (chargingNode.endsWith("input_suspend")) {
                raw != "1"
            } else {
                raw != "0"
            }
        } else {
            true
        }

        val currentLimitMa = if (limitNode != null) {
            val raw = privilegeBackend.readFile(limitNode).getOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (abs(raw) > 10_000L) (abs(raw) / 1000).toInt() else abs(raw).toInt()
        } else {
            0
        }

        ChargeControlStatus(
            isSupported = privilegeBackend.tier == PrivilegeTier.ROOT,
            isChargingEnabled = isChargingEnabled,
            isBypassActive = !isChargingEnabled,
            currentLimitMa = currentLimitMa,
            activeChargingNode = chargingNode,
            activeLimitNode = limitNode
        )
    }

    suspend fun setChargingEnabled(enabled: Boolean): Result<Unit> = withContext(ioDispatcher) {
        if (privilegeBackend.tier != PrivilegeTier.ROOT) {
            return@withContext Result.failure(IllegalStateException("Root privilege required for charge control"))
        }

        val node = findWorkingChargingNode()
            ?: return@withContext Result.failure(UnsupportedOperationException("No compatible charge control sysfs node found"))

        val valueToWrite = if (node.endsWith("input_suspend")) {
            if (enabled) "0" else "1"
        } else {
            if (enabled) "1" else "0"
        }

        val writeResult = privilegeBackend.writeFile(node, valueToWrite)
        if (writeResult.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(writeResult.exceptionOrNull() ?: RuntimeException("Failed to write to $node"))
        }
    }

    suspend fun setBypassCharging(bypassActive: Boolean): Result<Unit> {
        return setChargingEnabled(!bypassActive)
    }

    suspend fun setChargeCurrentLimit(limitMa: Int): Result<Unit> = withContext(ioDispatcher) {
        if (privilegeBackend.tier != PrivilegeTier.ROOT) {
            return@withContext Result.failure(IllegalStateException("Root privilege required for charge current control"))
        }

        val node = findWorkingLimitNode()
            ?: return@withContext Result.failure(UnsupportedOperationException("No compatible charge current limit sysfs node found"))

        val valueToWrite = if (limitMa <= 0) {
            "0"
        } else {
            // Kernel current nodes expect microamps
            (limitMa * 1000).toString()
        }

        val writeResult = privilegeBackend.writeFile(node, valueToWrite)
        if (writeResult.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(writeResult.exceptionOrNull() ?: RuntimeException("Failed to write to $node"))
        }
    }

    private suspend fun findWorkingChargingNode(): String? {
        for (node in chargingControlNodes) {
            val content = privilegeBackend.readFile(node).getOrNull()
            if (!content.isNullOrBlank()) {
                return node
            }
        }
        return null
    }

    private suspend fun findWorkingLimitNode(): String? {
        for (node in chargeLimitNodes) {
            val content = privilegeBackend.readFile(node).getOrNull()
            if (!content.isNullOrBlank()) {
                return node
            }
        }
        return null
    }
}
