package com.mahin.hytune.domain.detection

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class GameClassifierTest {

    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var appRuleDao: AppRuleDao
    private lateinit var classifier: GameClassifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appRuleDao = db.appRuleDao()
        classifier = GameClassifier(context, appRuleDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun isApplicationInfoGame_identifiesCategoryGame() {
        val appInfo = ApplicationInfo().apply {
            category = ApplicationInfo.CATEGORY_GAME
        }
        assertTrue(classifier.isApplicationInfoGame(appInfo))
    }

    @Test
    fun isApplicationInfoGame_identifiesFlagIsGame() {
        @Suppress("DEPRECATION")
        val appInfo = ApplicationInfo().apply {
            category = ApplicationInfo.CATEGORY_UNDEFINED
            flags = ApplicationInfo.FLAG_IS_GAME
        }
        assertTrue(classifier.isApplicationInfoGame(appInfo))
    }

    @Test
    fun isApplicationInfoGame_returnsFalseForRegularApp() {
        val appInfo = ApplicationInfo().apply {
            category = ApplicationInfo.CATEGORY_PRODUCTIVITY
            flags = 0
        }
        assertFalse(classifier.isApplicationInfoGame(appInfo))
    }

    @Test
    fun isGame_respectsDatabaseOverride() = runTest {
        val pkg = "com.custom.app"
        appRuleDao.upsertAppRule(
            AppRuleEntity(
                packageName = pkg,
                profileId = "performance",
                isGame = true
            )
        )

        assertTrue(classifier.isGame(pkg))
    }

    @Test
    fun isGame_returnsFalseWhenNoRuleAndNotInstalled() = runTest {
        assertFalse(classifier.isGame("com.nonexistent.app.xyz"))
    }
}
