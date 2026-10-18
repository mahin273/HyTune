package com.mahin.hytune.ui.dashboard

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.sampler.SystemSamplerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePrivilegeBackend: FakePrivilegeBackend
    private lateinit var hardwareProbe: HardwareProbe
    private lateinit var samplerEngine: SystemSamplerEngine
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var fakeProfileDao: FakeProfileDao
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakePrivilegeBackend = FakePrivilegeBackend().apply {
            setFile("/proc/cpuinfo", "Hardware\t: Qualcomm Technologies, Inc SM8550\nprocessor\t: 0\n")
            setFile("/proc/stat", "cpu  1000 200 300 4000 50 10 20 0 0 0\n")
            setFile("/proc/meminfo", "MemTotal: 8000000 kB\nMemFree: 2000000 kB\nMemAvailable: 4000000 kB\n")
            setFile("/sys/class/power_supply/battery/capacity", "85\n")
            setFile("/sys/class/power_supply/battery/temp", "320\n")
            setFile("/sys/class/power_supply/battery/voltage_now", "4000000\n")
            setFile("/sys/class/power_supply/battery/current_now", "500000\n")
            setFile("/sys/class/power_supply/battery/status", "Discharging\n")
        }

        hardwareProbe = HardwareProbe(fakePrivilegeBackend)
        samplerEngine = SystemSamplerEngine(fakePrivilegeBackend, testDispatcher)

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_dashboard_settings.preferences_pb") }
        )
        settingsRepository = AppSettingsRepository(testDataStore)
        fakeProfileDao = FakeProfileDao()

        viewModel = DashboardViewModel(
            hardwareProbe = hardwareProbe,
            samplerEngine = samplerEngine,
            settingsRepository = settingsRepository,
            profileDao = fakeProfileDao
        )
        viewModel.stopSampling()
    }

    @After
    fun tearDown() {
        viewModel.stopSampling()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoadingAndHardwareProbe() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertNotNull(state.hardwareSummary)
        assertTrue(state.hardwareSummary!!.socName.contains("SM8550") || state.hardwareSummary!!.totalCores > 0)
    }

    @Test
    fun testTelemetryFlowUpdatesLatestSnapshotAndLoading() = runTest(testDispatcher) {
        val snapshot = samplerEngine.sampleOnce()
        viewModel.processSnapshot(snapshot)

        val state = viewModel.uiState.value
        assertNotNull(state.latestSnapshot)
        assertFalse(state.isLoading)
        assertEquals(85, state.latestSnapshot!!.battery?.percent)
    }

    @Test
    fun testRollingHistoryBuffersAreBoundedToMaxPoints() = runTest(testDispatcher) {
        repeat(35) { i ->
            fakePrivilegeBackend.setFile(
                "/proc/stat",
                "cpu  ${2000 + i * 200} 200 300 ${5000 + i * 100} 50 10 20 0 0 0\n"
            )
            val snapshot = samplerEngine.sampleOnce()
            viewModel.processSnapshot(snapshot)
        }

        // Verify state bound
        val state = viewModel.uiState.value
        assertEquals(DashboardUiState.MAX_HISTORY_POINTS, state.cpuLoadHistory.size)
        assertEquals(DashboardUiState.MAX_HISTORY_POINTS, state.ramUsageHistory.size)
        assertEquals(DashboardUiState.MAX_HISTORY_POINTS, state.tempHistory.size)
        assertEquals(DashboardUiState.MAX_HISTORY_POINTS, state.batteryWattageHistory.size)
    }

    @Test
    fun testStartSamplingStreamCollectsSnapshots() = runTest(testDispatcher) {
        viewModel.startSampling(intervalMs = 10L)
        testScheduler.advanceTimeBy(15L)
        viewModel.stopSampling()

        val state = viewModel.uiState.value
        assertNotNull(state.latestSnapshot)
    }

    @Test
    fun testProfileSelectionUpdatesSettings() = runTest(testDispatcher) {
        viewModel.onProfileSelected("perf")
        val currentSettings = settingsRepository.settings.first { it.activeProfileId == "perf" }
        assertEquals("perf", currentSettings.activeProfileId)
    }

    @Test
    fun testProfilesFromDaoAreReflectedInState() = runTest(testDispatcher) {
        val testProfile = ProfileEntity(
            id = "custom_1",
            name = "Extreme Gaming",
            isBuiltIn = false,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        fakeProfileDao.upsertProfile(testProfile)

        val state = viewModel.uiState.value
        assertTrue(state.profiles.any { it.id == "custom_1" })
    }

    private class FakeProfileDao : ProfileDao {
        private val profilesFlow = MutableStateFlow<List<ProfileEntity>>(emptyList())

        override suspend fun upsertProfile(profile: ProfileEntity) {
            profilesFlow.update { current ->
                current.filterNot { it.id == profile.id } + profile
            }
        }

        override suspend fun upsertProfiles(profiles: List<ProfileEntity>) {
            profiles.forEach { upsertProfile(it) }
        }

        override fun getAllProfiles(): Flow<List<ProfileEntity>> = profilesFlow

        override suspend fun getProfileById(id: String): ProfileEntity? =
            profilesFlow.value.firstOrNull { it.id == id }

        override suspend fun getBuiltInProfiles(): List<ProfileEntity> =
            profilesFlow.value.filter { it.isBuiltIn }

        override suspend fun deleteProfile(profile: ProfileEntity) {
            profilesFlow.update { current -> current.filterNot { it.id == profile.id } }
        }

        override suspend fun deleteCustomProfileById(id: String): Int {
            val initialSize = profilesFlow.value.size
            profilesFlow.update { current -> current.filterNot { it.id == id && !it.isBuiltIn } }
            return initialSize - profilesFlow.value.size
        }
    }
}
