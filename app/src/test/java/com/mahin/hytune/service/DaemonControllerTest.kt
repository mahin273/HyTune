package com.mahin.hytune.service

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.service.battery.BatteryOptimizationManager
import com.mahin.hytune.service.model.DaemonState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class DaemonControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var daemonController: DaemonController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_daemon_settings.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)
        batteryOptimizationManager = BatteryOptimizationManager(context)
        daemonController = DaemonController(
            context = context,
            appSettingsRepository = appSettingsRepository,
            batteryOptimizationManager = batteryOptimizationManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isStopped() {
        assertEquals(DaemonState.STOPPED, daemonController.daemonState.value)
        assertFalse(daemonController.isRunning())
    }

    @Test
    fun updateState_modifiesStateFlow() {
        daemonController.updateState(DaemonState.RUNNING)
        assertEquals(DaemonState.RUNNING, daemonController.daemonState.value)
        assertTrue(daemonController.isRunning())

        daemonController.updateState(DaemonState.STOPPED)
        assertEquals(DaemonState.STOPPED, daemonController.daemonState.value)
        assertFalse(daemonController.isRunning())
    }

    @Test
    fun startDaemon_updatesStateAndPersistsPreference() = runTest(testDispatcher) {
        daemonController.startDaemon()

        val settings = appSettingsRepository.settings.first()
        assertTrue(settings.daemonServiceEnabled)
    }

    @Test
    fun stopDaemon_updatesStateAndPersistsPreference() = runTest(testDispatcher) {
        daemonController.stopDaemon()

        val settings = appSettingsRepository.settings.first()
        assertFalse(settings.daemonServiceEnabled)
    }

    @Test
    fun batteryOptimizationQueries_delegateProperly() {
        val ignored = daemonController.isIgnoringBatteryOptimizations()
        assertNotNull(ignored)

        val intent = daemonController.createRequestIgnoreBatteryOptimizationsIntent()
        assertNotNull(intent)
    }
}
