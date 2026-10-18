package com.mahin.hytune.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.domain.detection.GameClassifier
import com.mahin.hytune.domain.profile.AppRuleRepository
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.ui.model.AppItemUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class PerAppRulesViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var gameClassifier: GameClassifier
    private lateinit var viewModel: PerAppRulesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_vm.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)
        appRuleRepository = AppRuleRepository(db.appRuleDao(), testDispatcher)
        profileRepository = ProfileRepository(db.profileDao(), appSettingsRepository, testDispatcher)
        gameClassifier = GameClassifier(context, db.appRuleDao())

        viewModel = PerAppRulesViewModel(
            context = context,
            appRuleRepository = appRuleRepository,
            profileRepository = profileRepository,
            appSettingsRepository = appSettingsRepository,
            gameClassifier = gameClassifier,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun searchQuery_filtersApps() = runTest(testDispatcher) {
        val testApps = listOf(
            AppItemUiModel("com.spotify.music", "Spotify", isGame = false),
            AppItemUiModel("com.epicgames.fortnite", "Fortnite", isGame = true)
        )
        viewModel.setInstalledAppsForTesting(testApps)

        viewModel.setSearchQuery("fort")
        val state = viewModel.uiState.first { it.searchQuery == "fort" }
        assertEquals(1, state.apps.size)
        assertEquals("Fortnite", state.apps[0].appLabel)
    }

    @Test
    fun filterType_filtersGamesAndConfigured() = runTest(testDispatcher) {
        val testApps = listOf(
            AppItemUiModel("com.app.chat", "ChatApp", isGame = false),
            AppItemUiModel("com.app.game", "GameApp", isGame = true)
        )
        viewModel.setInstalledAppsForTesting(testApps)

        viewModel.setFilterType(AppFilterType.GAMES)
        val gamesState = viewModel.uiState.first { it.filterType == AppFilterType.GAMES }
        assertEquals(1, gamesState.apps.size)
        assertEquals("GameApp", gamesState.apps[0].appLabel)

        // Add a rule to ChatApp
        appRuleRepository.upsertRule(
            AppRuleEntity("com.app.chat", "powersave", isGame = false, targetRefreshRate = 60, dndEnabled = false)
        )

        viewModel.setFilterType(AppFilterType.CONFIGURED)
        val configuredState = viewModel.uiState.first { 
            it.filterType == AppFilterType.CONFIGURED && it.apps.isNotEmpty() 
        }
        assertEquals(1, configuredState.apps.size)
        assertEquals("ChatApp", configuredState.apps[0].appLabel)
    }

    @Test
    fun togglePerAppProfilesEnabled_updatesPreference() = runTest(testDispatcher) {
        viewModel.togglePerAppProfilesEnabled(true)
        val settings = appSettingsRepository.settings.first { it.perAppProfilesEnabled }
        assertTrue(settings.perAppProfilesEnabled)
    }

    @Test
    fun removeAppRule_deletesRuleFromDb() = runTest(testDispatcher) {
        appRuleRepository.upsertRule(
            AppRuleEntity("com.test.remove", "fast", isGame = true, targetRefreshRate = 120, dndEnabled = true)
        )
        assertNotNull(appRuleRepository.getRuleDirect("com.test.remove"))

        viewModel.removeAppRule("com.test.remove")
        val deleted = appRuleRepository.getRule("com.test.remove").first { it == null }
        assertNull(deleted)
    }
}
