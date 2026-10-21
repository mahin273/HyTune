package com.mahin.hytune.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: AppSettingsRepository

    @Before
    fun setUp() {
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_settings.preferences_pb") }
        )
        repository = AppSettingsRepository(testDataStore)
    }

    @Test
    fun defaultSettings_areProperlyInitialized() = runTest {
        val settings = repository.settings.first()

        assertEquals("balance", settings.activeProfileId)
        assertTrue(settings.temperatureUnitCelsius)
        assertFalse(settings.floatingMonitorEnabled)
        assertEquals(2000L, settings.samplingIntervalMs)
        assertEquals(15, settings.safetyRollbackTimeoutSec)
        assertFalse(settings.perAppProfilesEnabled)
        assertTrue(settings.dynamicThemeEnabled)
    }

    @Test
    fun setActiveProfileId_updatesAndEmitsNewValue() = runTest {
        repository.setActiveProfileId("perf")

        val settings = repository.settings.first()
        assertEquals("perf", settings.activeProfileId)
    }

    @Test
    fun setTemperatureUnitCelsius_updatesPreference() = runTest {
        repository.setTemperatureUnitCelsius(false)

        val settings = repository.settings.first()
        assertFalse(settings.temperatureUnitCelsius)
    }

    @Test
    fun setFloatingMonitorEnabled_and_interval() = runTest {
        repository.setFloatingMonitorEnabled(true)
        repository.setSamplingIntervalMs(500L)
        repository.setSafetyRollbackTimeoutSec(30)

        val settings = repository.settings.first()
        assertTrue(settings.floatingMonitorEnabled)
        assertEquals(500L, settings.samplingIntervalMs)
        assertEquals(30, settings.safetyRollbackTimeoutSec)
    }
}
