package com.mahin.hytune.ui.daemon

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.service.DaemonController
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
class DaemonViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var daemonController: DaemonController
    private lateinit var viewModel: DaemonViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_vm_daemon.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)
        batteryOptimizationManager = BatteryOptimizationManager(context)
        daemonController = DaemonController(
            context = context,
            appSettingsRepository = appSettingsRepository,
            batteryOptimizationManager = batteryOptimizationManager
        )
        viewModel = DaemonViewModel(
            daemonController = daemonController,
            appSettingsRepository = appSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_matchesControllerAndSettings() = runTest(testDispatcher) {
        assertEquals(DaemonState.STOPPED, viewModel.daemonState.value)
        val settings = appSettingsRepository.settings.first()
        assertFalse(settings.daemonServiceEnabled)
        assertTrue(settings.daemonNotificationDetailsEnabled)
    }

    @Test
    fun toggleDaemon_startsAndStopsService() = runTest(testDispatcher) {
        viewModel.toggleDaemon(true).join()
        val settingsAfterStart = appSettingsRepository.settings.first()
        assertTrue(settingsAfterStart.daemonServiceEnabled)

        viewModel.toggleDaemon(false).join()
        val settingsAfterStop = appSettingsRepository.settings.first()
        assertFalse(settingsAfterStop.daemonServiceEnabled)
    }

    @Test
    fun setNotificationDetailsEnabled_updatesRepository() = runTest(testDispatcher) {
        viewModel.setNotificationDetailsEnabled(false).join()

        val settings = appSettingsRepository.settings.first()
        assertFalse(settings.daemonNotificationDetailsEnabled)
    }

    @Test
    fun batteryOptimizationStatus_canBeRefreshed() = runTest(testDispatcher) {
        viewModel.refreshBatteryOptimizationStatus()
        assertNotNull(viewModel.isBatteryOptimizationIgnored.value)

        val intent = viewModel.createRequestIgnoreBatteryOptimizationsIntent()
        assertNotNull(intent)
    }
}
