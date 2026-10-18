package com.mahin.hytune.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
                gracefulShutdownOccurred = preferences[PreferencesKeys.GRACEFUL_SHUTDOWN_OCCURRED] ?: false
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
}
