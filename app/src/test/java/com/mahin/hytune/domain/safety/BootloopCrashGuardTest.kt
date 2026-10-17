package com.mahin.hytune.domain.safety

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BootloopCrashGuardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var crashGuard: BootloopCrashGuard

    @Before
    fun setUp() {
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_settings.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)
        crashGuard = BootloopCrashGuard(appSettingsRepository)
    }

    @Test
    fun cleanStartup_noCrashesDetected() = runTest {
        val isSafeMode = crashGuard.onAppOrBootStart().getOrThrow()
        assertFalse(isSafeMode)

        val settings = appSettingsRepository.settings.first()
        assertEquals(0, settings.bootloopCrashCount)
        assertFalse(settings.safeRecoveryModeActive)
    }

    @Test
    fun midApplyCrash_incrementsCrashCount() = runTest {
        // Simulate app crashing during hardware write
        crashGuard.recordApplyAttempt(timestampMs = System.currentTimeMillis())
        assertTrue(appSettingsRepository.settings.first().isApplyingSettings)

        // Simulate app/system restart
        val isSafeMode = crashGuard.onAppOrBootStart().getOrThrow()
        assertFalse(isSafeMode)

        val settings = appSettingsRepository.settings.first()
        assertEquals(1, settings.bootloopCrashCount)
        assertFalse(settings.isApplyingSettings)
        assertFalse(settings.safeRecoveryModeActive)
    }

    @Test
    fun rebootDuringStabilityWindow_incrementsCrashCount() = runTest {
        val applyTime = 1000000L
        crashGuard.recordApplyAttempt(timestampMs = applyTime)
        crashGuard.markApplyCompleted()

        // Unexpected reboot 1 minute later (inside 5 min stability window)
        val rebootTime = applyTime + 60 * 1000L
        val isSafeMode = crashGuard.onAppOrBootStart(currentTimeEpochMs = rebootTime).getOrThrow()
        assertFalse(isSafeMode)

        val settings = appSettingsRepository.settings.first()
        assertEquals(1, settings.bootloopCrashCount)
    }

    @Test
    fun consecutiveCrashes_triggersSafeRecoveryModeAtThreshold() = runTest {
        // First crash
        crashGuard.recordApplyAttempt(timestampMs = 1000000L)
        crashGuard.onAppOrBootStart(currentTimeEpochMs = 1050000L)
        assertEquals(1, appSettingsRepository.settings.first().bootloopCrashCount)

        // Second crash
        crashGuard.recordApplyAttempt(timestampMs = 1100000L)
        val isSafeMode = crashGuard.onAppOrBootStart(currentTimeEpochMs = 1150000L).getOrThrow()

        assertTrue("Safe recovery mode should be triggered at threshold 2", isSafeMode)
        val settings = appSettingsRepository.settings.first()
        assertEquals(2, settings.bootloopCrashCount)
        assertTrue(settings.safeRecoveryModeActive)
    }

    @Test
    fun gracefulShutdown_withinStabilityWindow_doesNotIncrementCrashCount() = runTest {
        val applyTime = 1000000L
        crashGuard.recordApplyAttempt(timestampMs = applyTime)
        crashGuard.markApplyCompleted()

        // User normally powers off device
        crashGuard.recordGracefulShutdown()
        assertTrue(appSettingsRepository.settings.first().gracefulShutdownOccurred)

        // System powers on again
        val rebootTime = applyTime + 60 * 1000L
        val isSafeMode = crashGuard.onAppOrBootStart(currentTimeEpochMs = rebootTime).getOrThrow()

        assertFalse(isSafeMode)
        val settings = appSettingsRepository.settings.first()
        assertEquals(0, settings.bootloopCrashCount)
        assertFalse(settings.gracefulShutdownOccurred)
    }

    @Test
    fun acknowledgeStability_clearsCrashCounterAndSafeMode() = runTest {
        appSettingsRepository.setBootloopCrashCount(2)
        appSettingsRepository.setSafeRecoveryModeActive(true)

        crashGuard.acknowledgeStability()

        val settings = appSettingsRepository.settings.first()
        assertEquals(0, settings.bootloopCrashCount)
        assertFalse(settings.safeRecoveryModeActive)
    }

    @Test
    fun resetCrashCounter_manuallyResetsState() = runTest {
        appSettingsRepository.setBootloopCrashCount(3)
        appSettingsRepository.setSafeRecoveryModeActive(true)

        crashGuard.resetCrashCounter()

        val settings = appSettingsRepository.settings.first()
        assertEquals(0, settings.bootloopCrashCount)
        assertFalse(settings.safeRecoveryModeActive)
    }
}
