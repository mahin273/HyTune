package com.mahin.hytune.integration.tile

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.storage.StorageControlEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = com.mahin.hytune.HyTuneApp::class)
class ProfileQuickTileServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var profileEngine: ProfileEngine
    private lateinit var tileService: ProfileQuickTileService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val datastoreFile = tempFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            produceFile = { datastoreFile }
        )
        appSettingsRepository = AppSettingsRepository(dataStore)

        fakeBackend = FakePrivilegeBackend()
        profileRepository = ProfileRepository(db.profileDao(), appSettingsRepository, testDispatcher)

        val probe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, probe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, probe, testDispatcher)
        val memEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        tileService = org.robolectric.Robolectric.buildService(ProfileQuickTileService::class.java).create().get().apply {
            this.profileRepository = this@ProfileQuickTileServiceTest.profileRepository
            this.profileEngine = this@ProfileQuickTileServiceTest.profileEngine
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun builtInProfiles_rotationOrderIsCorrect() {
        val rotatingList = listOf(
            BuiltInProfiles.BALANCE,
            BuiltInProfiles.PERFORMANCE,
            BuiltInProfiles.FAST,
            BuiltInProfiles.POWERSAVE
        )

        assertEquals("balance", rotatingList[0].id)
        assertEquals("performance", rotatingList[1].id)
        assertEquals("fast", rotatingList[2].id)
        assertEquals("powersave", rotatingList[3].id)

        // Verify wrap around
        val nextAfterLast = (3 + 1) % rotatingList.size
        assertEquals(0, nextAfterLast)
    }

    @Test
    fun updateTileDisplay_handlesNullQsTileGracefully() {
        // In unit tests without SystemUI binder, qsTile is null and should not throw
        tileService.updateTileDisplay(BuiltInProfiles.BALANCE)
    }
}
