package com.mahin.hytune.ui.appmanager

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.appmanager.AppManagerRepository
import com.mahin.hytune.domain.appmanager.model.AppSortOrder
import com.mahin.hytune.domain.appmanager.model.AppSummary
import com.mahin.hytune.domain.appmanager.model.AppTypeFilter
import com.mahin.hytune.domain.detection.GameClassifier
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AppManagerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var gameClassifier: GameClassifier
    private lateinit var repository: AppManagerRepository
    private lateinit var viewModel: AppManagerViewModel

    private val sampleApps = listOf(
        AppSummary(
            packageName = "com.spotify.music",
            appLabel = "Spotify",
            versionName = "8.8.0",
            versionCode = 880,
            isSystemApp = false,
            isUpdatedSystemApp = false,
            isEnabled = true,
            isSuspended = false,
            isGame = false,
            installTimeEpochMs = 1000L,
            updateTimeEpochMs = 2000L,
            targetSdk = 33,
            minSdk = 24,
            uid = 10001
        ),
        AppSummary(
            packageName = "com.android.settings",
            appLabel = "Settings",
            versionName = "13.0",
            versionCode = 1300,
            isSystemApp = true,
            isUpdatedSystemApp = false,
            isEnabled = true,
            isSuspended = false,
            isGame = false,
            installTimeEpochMs = 500L,
            updateTimeEpochMs = 500L,
            targetSdk = 33,
            minSdk = 26,
            uid = 1000
        ),
        AppSummary(
            packageName = "com.riotgames.league.wildrift",
            appLabel = "Wild Rift",
            versionName = "4.2.0",
            versionCode = 4200,
            isSystemApp = false,
            isUpdatedSystemApp = false,
            isEnabled = true,
            isSuspended = false,
            isGame = true,
            installTimeEpochMs = 3000L,
            updateTimeEpochMs = 4000L,
            targetSdk = 34,
            minSdk = 26,
            uid = 10050
        ),
        AppSummary(
            packageName = "com.bloatware.oem",
            appLabel = "OEM Bloat",
            versionName = "1.0",
            versionCode = 10,
            isSystemApp = true,
            isUpdatedSystemApp = false,
            isEnabled = false,
            isSuspended = false,
            isGame = false,
            installTimeEpochMs = 200L,
            updateTimeEpochMs = 200L,
            targetSdk = 29,
            minSdk = 21,
            uid = 10080
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameClassifier = GameClassifier(context, db.appRuleDao())
        repository = AppManagerRepository(context, gameClassifier, testDispatcher)
        viewModel = AppManagerViewModel(repository, testDispatcher)
        viewModel.setAppsForTesting(sampleApps)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_calculatesAggregateStatistics() = runTest(testDispatcher) {
        val state = viewModel.uiState.first { it.totalCount == 4 }
        assertEquals(4, state.totalCount)
        assertEquals(2, state.userAppCount)
        assertEquals(2, state.systemAppCount)
        assertEquals(1, state.disabledCount)
        assertEquals(1, state.gamesCount)
    }

    @Test
    fun searchQuery_filtersByLabelAndPackage() = runTest(testDispatcher) {
        viewModel.setSearchQuery("wild")
        val state = viewModel.uiState.first { it.searchQuery == "wild" }
        assertEquals(1, state.apps.size)
        assertEquals("Wild Rift", state.apps[0].appLabel)

        viewModel.setSearchQuery("com.bloat")
        val state2 = viewModel.uiState.first { it.searchQuery == "com.bloat" }
        assertEquals(1, state2.apps.size)
        assertEquals("OEM Bloat", state2.apps[0].appLabel)
    }

    @Test
    fun filter_filtersByUserSystemDisabledAndGame() = runTest(testDispatcher) {
        viewModel.setFilter(AppTypeFilter.USER)
        val userState = viewModel.uiState.first { it.filter == AppTypeFilter.USER }
        assertEquals(2, userState.apps.size)

        viewModel.setFilter(AppTypeFilter.SYSTEM)
        val systemState = viewModel.uiState.first { it.filter == AppTypeFilter.SYSTEM }
        assertEquals(2, systemState.apps.size)

        viewModel.setFilter(AppTypeFilter.DISABLED)
        val disabledState = viewModel.uiState.first { it.filter == AppTypeFilter.DISABLED }
        assertEquals(1, disabledState.apps.size)
        assertEquals("OEM Bloat", disabledState.apps[0].appLabel)

        viewModel.setFilter(AppTypeFilter.GAMES)
        val gamesState = viewModel.uiState.first { it.filter == AppTypeFilter.GAMES }
        assertEquals(1, gamesState.apps.size)
        assertEquals("Wild Rift", gamesState.apps[0].appLabel)
    }

    @Test
    fun sortOrder_sortsByNamePackageAndDates() = runTest(testDispatcher) {
        viewModel.setSortOrder(AppSortOrder.NAME_ASC)
        val nameAsc = viewModel.uiState.first { it.sortOrder == AppSortOrder.NAME_ASC }
        assertEquals("OEM Bloat", nameAsc.apps.first().appLabel)
        assertEquals("Wild Rift", nameAsc.apps.last().appLabel)

        viewModel.setSortOrder(AppSortOrder.INSTALL_TIME_DESC)
        val installDesc = viewModel.uiState.first { it.sortOrder == AppSortOrder.INSTALL_TIME_DESC }
        assertEquals("Wild Rift", installDesc.apps.first().appLabel) // 3000L

        viewModel.setSortOrder(AppSortOrder.TARGET_SDK_DESC)
        val sdkDesc = viewModel.uiState.first { it.sortOrder == AppSortOrder.TARGET_SDK_DESC }
        assertEquals("Wild Rift", sdkDesc.apps.first().appLabel) // SDK 34
    }

    @Test
    fun selectAndClearAppForInspection() = runTest(testDispatcher) {
        assertNull(viewModel.selectedAppDetails.value)

        viewModel.clearSelectedApp()
        assertNull(viewModel.selectedAppDetails.value)
    }
}
