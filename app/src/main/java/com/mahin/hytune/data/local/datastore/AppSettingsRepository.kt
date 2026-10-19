package com.mahin.hytune.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferencesKeys {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val TEMP_UNIT_CELSIUS = booleanPreferencesKey("temp_unit_celsius")
        val FLOATING_MONITOR_ENABLED = booleanPreferencesKey("floating_monitor_enabled")
        val SAMPLING_INTERVAL_MS = longPreferencesKey("sampling_interval_ms")
        val SAFETY_ROLLBACK_TIMEOUT_SEC = intPreferencesKey("safety_rollback_timeout_sec")
        val PER_APP_PROFILES_ENABLED = booleanPreferencesKey("per_app_profiles_enabled")
        val DYNAMIC_THEME_ENABLED = booleanPreferencesKey("dynamic_theme_enabled")
        val BOOTLOOP_CRASH_COUNT = intPreferencesKey("bootloop_crash_count")
        val LAST_APPLY_TIMESTAMP_EPOCH_MS = longPreferencesKey("last_apply_timestamp_epoch_ms")
        val IS_APPLYING_SETTINGS = booleanPreferencesKey("is_applying_settings")
        val SAFE_RECOVERY_MODE_ACTIVE = booleanPreferencesKey("safe_recovery_mode_active")
        val DRY_RUN_PREVIEW_ENABLED = booleanPreferencesKey("dry_run_preview_enabled")
        val BASELINE_SNAPSHOT_JSON = stringPreferencesKey("baseline_snapshot_json")
        val GRACEFUL_SHUTDOWN_OCCURRED = booleanPreferencesKey("graceful_shutdown_occurred")
        val DAEMON_SERVICE_ENABLED = booleanPreferencesKey("daemon_service_enabled")
        val DAEMON_NOTIFICATION_DETAILS_ENABLED = booleanPreferencesKey("daemon_notification_details_enabled")
        val CHARGE_LIMIT_ENABLED = booleanPreferencesKey("charge_limit_enabled")
        val CHARGE_LIMIT_PERCENT = intPreferencesKey("charge_limit_percent")
        val CHARGE_CURRENT_LIMIT_MA = intPreferencesKey("charge_current_limit_ma")
        val BYPASS_CHARGING_ENABLED = booleanPreferencesKey("bypass_charging_enabled")
        val OVERLAY_COMPACT_MODE = booleanPreferencesKey("overlay_compact_mode")
        val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        val OVERLAY_SHOW_CPU = booleanPreferencesKey("overlay_show_cpu")
        val OVERLAY_SHOW_GPU = booleanPreferencesKey("overlay_show_gpu")
        val OVERLAY_SHOW_BATTERY = booleanPreferencesKey("overlay_show_battery")
        val OVERLAY_SHOW_TEMP = booleanPreferencesKey("overlay_show_temp")
        val OVERLAY_SHOW_RAM = booleanPreferencesKey("overlay_show_ram")
        val OVERLAY_SHOW_FPS = booleanPreferencesKey("overlay_show_fps")
        val OVERLAY_POS_X = intPreferencesKey("overlay_pos_x")
        val OVERLAY_POS_Y = intPreferencesKey("overlay_pos_y")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                activeProfileId = preferences[PreferencesKeys.ACTIVE_PROFILE_ID] ?: "balance",
                temperatureUnitCelsius = preferences[PreferencesKeys.TEMP_UNIT_CELSIUS] ?: true,
                floatingMonitorEnabled = preferences[PreferencesKeys.FLOATING_MONITOR_ENABLED] ?: false,
                samplingIntervalMs = preferences[PreferencesKeys.SAMPLING_INTERVAL_MS] ?: 1000L,
                safetyRollbackTimeoutSec = preferences[PreferencesKeys.SAFETY_ROLLBACK_TIMEOUT_SEC] ?: 15,
                perAppProfilesEnabled = preferences[PreferencesKeys.PER_APP_PROFILES_ENABLED] ?: false,
                dynamicThemeEnabled = preferences[PreferencesKeys.DYNAMIC_THEME_ENABLED] ?: true,
                bootloopCrashCount = preferences[PreferencesKeys.BOOTLOOP_CRASH_COUNT] ?: 0,
                lastApplyTimestampEpochMs = preferences[PreferencesKeys.LAST_APPLY_TIMESTAMP_EPOCH_MS] ?: 0L,
                isApplyingSettings = preferences[PreferencesKeys.IS_APPLYING_SETTINGS] ?: false,
                safeRecoveryModeActive = preferences[PreferencesKeys.SAFE_RECOVERY_MODE_ACTIVE] ?: false,
                dryRunPreviewEnabled = preferences[PreferencesKeys.DRY_RUN_PREVIEW_ENABLED] ?: true,
                baselineSnapshotJson = preferences[PreferencesKeys.BASELINE_SNAPSHOT_JSON],
                gracefulShutdownOccurred = preferences[PreferencesKeys.GRACEFUL_SHUTDOWN_OCCURRED] ?: false,
                daemonServiceEnabled = preferences[PreferencesKeys.DAEMON_SERVICE_ENABLED] ?: false,
                daemonNotificationDetailsEnabled = preferences[PreferencesKeys.DAEMON_NOTIFICATION_DETAILS_ENABLED] ?: true,
                chargeLimitEnabled = preferences[PreferencesKeys.CHARGE_LIMIT_ENABLED] ?: false,
                chargeLimitPercent = preferences[PreferencesKeys.CHARGE_LIMIT_PERCENT] ?: 80,
                chargeCurrentLimitMa = preferences[PreferencesKeys.CHARGE_CURRENT_LIMIT_MA] ?: 0,
                bypassChargingEnabled = preferences[PreferencesKeys.BYPASS_CHARGING_ENABLED] ?: false,
                overlayCompactMode = preferences[PreferencesKeys.OVERLAY_COMPACT_MODE] ?: true,
                overlayOpacity = preferences[PreferencesKeys.OVERLAY_OPACITY] ?: 0.85f,
                overlayShowCpu = preferences[PreferencesKeys.OVERLAY_SHOW_CPU] ?: true,
                overlayShowGpu = preferences[PreferencesKeys.OVERLAY_SHOW_GPU] ?: true,
                overlayShowBattery = preferences[PreferencesKeys.OVERLAY_SHOW_BATTERY] ?: true,
                overlayShowTemp = preferences[PreferencesKeys.OVERLAY_SHOW_TEMP] ?: true,
                overlayShowRam = preferences[PreferencesKeys.OVERLAY_SHOW_RAM] ?: true,
                overlayShowFps = preferences[PreferencesKeys.OVERLAY_SHOW_FPS] ?: true,
                overlayPositionX = preferences[PreferencesKeys.OVERLAY_POS_X] ?: 100,
                overlayPositionY = preferences[PreferencesKeys.OVERLAY_POS_Y] ?: 200
            )
        }

    suspend fun setActiveProfileId(profileId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_PROFILE_ID] = profileId
        }
    }

    suspend fun setTemperatureUnitCelsius(celsius: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMP_UNIT_CELSIUS] = celsius
        }
    }

    suspend fun setFloatingMonitorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FLOATING_MONITOR_ENABLED] = enabled
        }
    }

    suspend fun setSamplingIntervalMs(intervalMs: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAMPLING_INTERVAL_MS] = intervalMs
        }
    }

    suspend fun setSafetyRollbackTimeoutSec(timeoutSec: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAFETY_ROLLBACK_TIMEOUT_SEC] = timeoutSec
        }
    }

    suspend fun setPerAppProfilesEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PER_APP_PROFILES_ENABLED] = enabled
        }
    }

    suspend fun setDynamicThemeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_THEME_ENABLED] = enabled
        }
    }

    suspend fun setBootloopCrashCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOOTLOOP_CRASH_COUNT] = count
        }
    }

    suspend fun setLastApplyTimestampEpochMs(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_APPLY_TIMESTAMP_EPOCH_MS] = timestamp
        }
    }

    suspend fun setIsApplyingSettings(isApplying: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_APPLYING_SETTINGS] = isApplying
        }
    }

    suspend fun setSafeRecoveryModeActive(active: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAFE_RECOVERY_MODE_ACTIVE] = active
        }
    }

    suspend fun setDryRunPreviewEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DRY_RUN_PREVIEW_ENABLED] = enabled
        }
    }

    suspend fun setBaselineSnapshotJson(json: String?) {
        dataStore.edit { preferences ->
            if (json == null) {
                preferences.remove(PreferencesKeys.BASELINE_SNAPSHOT_JSON)
            } else {
                preferences[PreferencesKeys.BASELINE_SNAPSHOT_JSON] = json
            }
        }
    }

    suspend fun setGracefulShutdownOccurred(graceful: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GRACEFUL_SHUTDOWN_OCCURRED] = graceful
        }
    }

    suspend fun setDaemonServiceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAEMON_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setDaemonNotificationDetailsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAEMON_NOTIFICATION_DETAILS_ENABLED] = enabled
        }
    }

    suspend fun setChargeLimitEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHARGE_LIMIT_ENABLED] = enabled
        }
    }

    suspend fun setChargeLimitPercent(percent: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHARGE_LIMIT_PERCENT] = percent
        }
    }

    suspend fun setChargeCurrentLimitMa(limitMa: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHARGE_CURRENT_LIMIT_MA] = limitMa
        }
    }

    suspend fun setBypassChargingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BYPASS_CHARGING_ENABLED] = enabled
        }
    }

    suspend fun setOverlayCompactMode(compact: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERLAY_COMPACT_MODE] = compact
        }
    }

    suspend fun setOverlayOpacity(opacity: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERLAY_OPACITY] = opacity
        }
    }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERLAY_POS_X] = x
            preferences[PreferencesKeys.OVERLAY_POS_Y] = y
        }
    }

    suspend fun setOverlayMetricVisibility(
        showCpu: Boolean,
        showGpu: Boolean,
        showBattery: Boolean,
        showTemp: Boolean,
        showRam: Boolean,
        showFps: Boolean = true
    ) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERLAY_SHOW_CPU] = showCpu
            preferences[PreferencesKeys.OVERLAY_SHOW_GPU] = showGpu
            preferences[PreferencesKeys.OVERLAY_SHOW_BATTERY] = showBattery
            preferences[PreferencesKeys.OVERLAY_SHOW_TEMP] = showTemp
            preferences[PreferencesKeys.OVERLAY_SHOW_RAM] = showRam
            preferences[PreferencesKeys.OVERLAY_SHOW_FPS] = showFps
        }
    }
}
