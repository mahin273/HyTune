package com.mahin.hytune.domain.profile

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.domain.profile.model.CpuClusterProfileConfig
import com.mahin.hytune.domain.profile.model.FrequencyTarget
import com.mahin.hytune.domain.profile.model.ProfileMode
import com.mahin.hytune.domain.profile.model.TuningProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class ProfileRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: HyTuneDatabase
    private lateinit var profileDao: ProfileDao
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileDao = db.profileDao()

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_settings.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)

        repository = ProfileRepository(
            profileDao = profileDao,
            appSettingsRepository = appSettingsRepository,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedBuiltInProfilesIfEmpty_seedsFourFactoryProfiles() = runTest(testDispatcher) {
        val seedResult = repository.seedBuiltInProfilesIfEmpty()
        assertTrue(seedResult.isSuccess)

        val builtIns = profileDao.getBuiltInProfiles()
        assertEquals(4, builtIns.size)
        assertTrue(builtIns.any { it.id == "powersave" })
        assertTrue(builtIns.any { it.id == "balance" })
        assertTrue(builtIns.any { it.id == "performance" })
        assertTrue(builtIns.any { it.id == "fast" })
    }

    @Test
    fun seedBuiltInProfilesIfEmpty_doesNotDuplicateIfAlreadySeeded() = runTest(testDispatcher) {
        repository.seedBuiltInProfilesIfEmpty()
        assertEquals(4, profileDao.getBuiltInProfiles().size)

        repository.seedBuiltInProfilesIfEmpty()
        assertEquals(4, profileDao.getBuiltInProfiles().size)
    }

    @Test
    fun getAllProfiles_returnsBuiltIns_whenDatabaseEmpty() = runTest(testDispatcher) {
        val profiles = repository.getAllProfiles().first()
        assertEquals(4, profiles.size)
        assertEquals("Powersave", profiles[0].name)
        assertEquals("Balance", profiles[1].name)
    }

    @Test
    fun getProfileById_returnsBuiltInDirectly() = runTest(testDispatcher) {
        val result = repository.getProfileById("fast")
        assertTrue(result.isSuccess)

        val profile = result.getOrThrow()
        assertNotNull(profile)
        assertEquals("fast", profile!!.id)
        assertEquals(ProfileMode.FAST, profile.mode)
        assertTrue(profile.isBuiltIn)
    }

    @Test
    fun saveCustomProfile_insertsIntoDatabase() = runTest(testDispatcher) {
        val custom = TuningProfile(
            id = "custom_battery_saver",
            name = "Extreme Battery",
            description = "Custom profile with extreme clock throttling",
            mode = ProfileMode.CUSTOM,
            isBuiltIn = false,
            cpuClusters = mapOf(
                0 to CpuClusterProfileConfig(
                    clusterId = 0,
                    governor = "powersave",
                    minFreqTarget = FrequencyTarget.AbsoluteKHz(300000L),
                    maxFreqTarget = FrequencyTarget.AbsoluteKHz(800000L)
                )
            )
        )

        val saveRes = repository.saveCustomProfile(custom)
        assertTrue(saveRes.isSuccess)

        val fetched = profileDao.getProfileById("custom_battery_saver")
        assertNotNull(fetched)
        assertEquals("Extreme Battery", fetched!!.name)
        assertFalse(fetched.isBuiltIn)
        assertEquals("powersave", fetched.cpuGovernor)
        assertEquals(300000L, fetched.cpuMinFreqKHz)
        assertEquals(800000L, fetched.cpuMaxFreqKHz)
    }

    @Test
    fun saveCustomProfile_throwsExceptionIfProfileIsBuiltIn() = runTest(testDispatcher) {
        val fakeBuiltIn = BuiltInProfiles.BALANCE.copy(name = "Modified Balance")
        val saveRes = repository.saveCustomProfile(fakeBuiltIn)
        assertTrue(saveRes.isFailure)
    }

    @Test
    fun deleteCustomProfile_removesFromDatabase() = runTest(testDispatcher) {
        val custom = TuningProfile(
            id = "temporary_tune",
            name = "Temp",
            description = "Will be deleted",
            mode = ProfileMode.CUSTOM,
            isBuiltIn = false
        )
        repository.saveCustomProfile(custom)
        assertNotNull(profileDao.getProfileById("temporary_tune"))

        val deleteRes = repository.deleteCustomProfile("temporary_tune")
        assertTrue(deleteRes.isSuccess)
        assertNull(profileDao.getProfileById("temporary_tune"))
    }

    @Test
    fun deleteCustomProfile_failsIfBuiltIn() = runTest(testDispatcher) {
        repository.seedBuiltInProfilesIfEmpty()

        val deleteRes = repository.deleteCustomProfile("balance")
        assertTrue(deleteRes.isFailure)
        assertNotNull(profileDao.getProfileById("balance"))
    }

    @Test
    fun setActiveProfile_updatesActiveProfileIdAndActiveProfile() = runTest(testDispatcher) {
        repository.seedBuiltInProfilesIfEmpty()

        val setRes = repository.setActiveProfile("performance")
        assertTrue(setRes.isSuccess)

        val activeId = repository.getActiveProfileId().first()
        assertEquals("performance", activeId)

        val activeProfile = repository.getActiveProfile().first()
        assertEquals("performance", activeProfile.id)
        assertEquals(ProfileMode.PERFORMANCE, activeProfile.mode)
    }
}
