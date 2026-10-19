package com.mahin.hytune.domain.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.battery.model.BatteryHealth
import com.mahin.hytune.domain.battery.model.BatteryState
import com.mahin.hytune.domain.battery.model.BatteryStatus
import com.mahin.hytune.domain.battery.model.PluggedType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class BatteryEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    private val sysfsSearchPaths = listOf(
        "/sys/class/power_supply/battery",
        "/sys/class/power_supply/bms"
    )

    suspend fun sample(): BatteryState = withContext(ioDispatcher) {
        val sysfsBase = findWorkingSysfsPath()
        if (sysfsBase != null && privilegeBackend.tier != PrivilegeTier.NONE) {
            val state = readFromSysfs(sysfsBase)
            if (state != null) {
                return@withContext state
            }
        }

        readFromBatteryManager()
    }

    private suspend fun findWorkingSysfsPath(): String? {
        for (path in sysfsSearchPaths) {
            val cap = privilegeBackend.readFile("$path/capacity").getOrNull()
            if (!cap.isNullOrBlank()) {
                return path
            }
        }
        return null
    }

    private suspend fun readFromSysfs(basePath: String): BatteryState? {
        val capRaw = privilegeBackend.readFile("$basePath/capacity").getOrNull()?.trim() ?: return null
        val level = capRaw.toIntOrNull() ?: return null

        val statusRaw = privilegeBackend.readFile("$basePath/status").getOrNull()?.trim() ?: "Unknown"
        val status = mapStatusString(statusRaw)

        val healthRaw = privilegeBackend.readFile("$basePath/health").getOrNull()?.trim() ?: "Good"
        val health = mapHealthString(healthRaw)

        val voltRaw = privilegeBackend.readFile("$basePath/voltage_now").getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val voltageMv = if (voltRaw > 100_000L) (voltRaw / 1000).toInt() else voltRaw.toInt()

        val currRaw = privilegeBackend.readFile("$basePath/current_now").getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val currentMaRaw = if (abs(currRaw) > 10_000L) (currRaw / 1000).toInt() else currRaw.toInt()

        // Normalize current: charging is positive, discharging is negative
        val currentMa = when (status) {
            BatteryStatus.CHARGING -> abs(currentMaRaw)
            BatteryStatus.DISCHARGING -> -abs(currentMaRaw)
            else -> currentMaRaw
        }

        val tempRaw = privilegeBackend.readFile("$basePath/temp").getOrNull()?.trim()?.toFloatOrNull() ?: 0f
        val temperatureC = com.mahin.hytune.domain.thermal.ThermalUtils.normalizeTemperature(tempRaw)

        val chargeCounter = privilegeBackend.readFile("$basePath/charge_counter").getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val technology = privilegeBackend.readFile("$basePath/technology").getOrNull()?.trim() ?: "Li-ion"

        val powerWatts = (voltageMv.toFloat() / 1000f) * (abs(currentMa).toFloat() / 1000f)

        return BatteryState(
            levelPercent = level.coerceIn(0, 100),
            status = status,
            health = health,
            pluggedType = if (status == BatteryStatus.CHARGING) PluggedType.AC else PluggedType.NONE,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerWatts = powerWatts,
            temperatureC = temperatureC,
            chargeCounterUah = chargeCounter,
            technology = technology
        )
    }

    private fun readFromBatteryManager(): BatteryState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (scale > 0 && levelRaw >= 0) (levelRaw * 100) / scale else 50

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
            else -> BatteryStatus.UNKNOWN
        }

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }

        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pluggedType = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> PluggedType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PluggedType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PluggedType.WIRELESS
            else -> PluggedType.NONE
        }

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000) ?: 4000
        val tempInt = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val temperatureC = tempInt / 10f

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        // BatteryManager current property is in microamperes
        val currentUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentMaRaw = currentUa / 1000

        val currentMa = when {
            status == BatteryStatus.CHARGING || pluggedType != PluggedType.NONE -> abs(currentMaRaw)
            status == BatteryStatus.DISCHARGING -> -abs(currentMaRaw)
            else -> currentMaRaw
        }

        val chargeCounter = (batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0).toLong()

        val powerWatts = (voltageMv.toFloat() / 1000f) * (abs(currentMa).toFloat() / 1000f)

        return BatteryState(
            levelPercent = level.coerceIn(0, 100),
            status = status,
            health = health,
            pluggedType = pluggedType,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerWatts = powerWatts,
            temperatureC = temperatureC,
            chargeCounterUah = chargeCounter,
            technology = technology
        )
    }

    private fun mapStatusString(status: String): BatteryStatus {
        return when (status.lowercase()) {
            "charging" -> BatteryStatus.CHARGING
            "discharging" -> BatteryStatus.DISCHARGING
            "full" -> BatteryStatus.FULL
            "not charging" -> BatteryStatus.NOT_CHARGING
            else -> BatteryStatus.UNKNOWN
        }
    }

    private fun mapHealthString(health: String): BatteryHealth {
        return when (health.lowercase()) {
            "good" -> BatteryHealth.GOOD
            "overheat" -> BatteryHealth.OVERHEAT
            "dead" -> BatteryHealth.DEAD
            "over voltage", "overvoltage" -> BatteryHealth.OVER_VOLTAGE
            "cold" -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }
    }
}
