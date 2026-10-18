package com.mahin.hytune.domain.profile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppRuleRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var appRuleDao: AppRuleDao
    private lateinit var repository: AppRuleRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appRuleDao = db.appRuleDao()
        repository = AppRuleRepository(appRuleDao, testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertRule_savesAndRetrievesRule() = runTest(testDispatcher) {
        val rule = AppRuleEntity(
            packageName = "com.supercell.brawlstars",
            profileId = "fast",
            isGame = true,
            targetRefreshRate = 120,
            dndEnabled = true
        )

        val result = repository.upsertRule(rule)
        assertTrue(result.isSuccess)

        val direct = repository.getRuleDirect("com.supercell.brawlstars")
        assertNotNull(direct)
        assertEquals("fast", direct!!.profileId)
        assertEquals(120, direct.targetRefreshRate)
        assertTrue(direct.dndEnabled)

        val flowRule = repository.getRule("com.supercell.brawlstars").first()
        assertEquals(direct, flowRule)
    }

    @Test
    fun deleteRule_removesRule() = runTest(testDispatcher) {
        val rule = AppRuleEntity(
            packageName = "com.test.remove",
            profileId = "powersave"
        )
        repository.upsertRule(rule)
        assertNotNull(repository.getRuleDirect("com.test.remove"))

        val deleteRes = repository.deleteRule("com.test.remove")
        assertTrue(deleteRes.isSuccess)

        assertNull(repository.getRuleDirect("com.test.remove"))
    }

    @Test
    fun setGame_updatesOrCreatesRuleWithGameFlag() = runTest(testDispatcher) {
        repository.setGame("com.new.game", isGame = true)
        val created = repository.getRuleDirect("com.new.game")
        assertNotNull(created)
        assertTrue(created!!.isGame)

        repository.setGame("com.new.game", isGame = false)
        val updated = repository.getRuleDirect("com.new.game")
        assertNotNull(updated)
        assertTrue(!updated!!.isGame)
    }

    @Test
    fun getAllRules_emitsSortedList() = runTest(testDispatcher) {
        repository.upsertRule(AppRuleEntity("com.b", "balance"))
        repository.upsertRule(AppRuleEntity("com.a", "powersave"))

        val all = repository.getAllRules().first()
        assertEquals(2, all.size)
        assertEquals("com.a", all[0].packageName)
        assertEquals("com.b", all[1].packageName)
    }
}
