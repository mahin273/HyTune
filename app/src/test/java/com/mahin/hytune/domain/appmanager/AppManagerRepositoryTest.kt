package com.mahin.hytune.domain.appmanager

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.detection.GameClassifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AppManagerRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var gameClassifier: GameClassifier
    private lateinit var repository: AppManagerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameClassifier = GameClassifier(context, db.appRuleDao())
        repository = AppManagerRepository(
            context = context,
            gameClassifier = gameClassifier,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getInstalledApps_discoversAndCategorizesPackages() = runTest(testDispatcher) {
        val pm = shadowOf(context.packageManager)

        val userApp = PackageInfo().apply {
            packageName = "com.example.userapp"
            versionName = "1.2.3"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.example.userapp"
                flags = 0
                enabled = true
                targetSdkVersion = 33
            }
        }
        pm.installPackage(userApp)

        val systemApp = PackageInfo().apply {
            packageName = "com.android.settings"
            versionName = "13.0"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.android.settings"
                flags = ApplicationInfo.FLAG_SYSTEM
                enabled = true
                targetSdkVersion = 33
            }
        }
        pm.installPackage(systemApp)

        val apps = repository.getInstalledApps(forceRefresh = true).first()
        assertTrue(apps.isNotEmpty())

        val foundUser = apps.find { it.packageName == "com.example.userapp" }
        assertNotNull(foundUser)
        assertFalse(foundUser!!.isSystemApp)
        assertEquals("1.2.3", foundUser.versionName)

        val foundSystem = apps.find { it.packageName == "com.android.settings" }
        assertNotNull(foundSystem)
        assertTrue(foundSystem!!.isSystemApp)
    }

    @Test
    fun getAppDetails_returnsGranularMetadataAndComponents() = runTest(testDispatcher) {
        val pm = shadowOf(context.packageManager)

        val detailedApp = PackageInfo().apply {
            packageName = "com.inspect.testapp"
            versionName = "2.0.0"
            firstInstallTime = 1600000000000L
            lastUpdateTime = 1610000000000L
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.inspect.testapp"
                sourceDir = "/data/app/com.inspect.testapp.apk"
                dataDir = "/data/user/0/com.inspect.testapp"
                flags = 0
                enabled = true
                targetSdkVersion = 34
                uid = 10123
            }
            requestedPermissions = arrayOf("android.permission.INTERNET", "android.permission.CAMERA")
            requestedPermissionsFlags = intArrayOf(
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                0 // Denied
            )
            activities = arrayOf(ActivityInfo().apply { name = "MainActivity" })
            services = arrayOf(ServiceInfo().apply { name = "WorkerService" })
        }
        pm.installPackage(detailedApp)

        val result = repository.getAppDetails("com.inspect.testapp")
        assertTrue(result.isSuccess)

        val details = result.getOrThrow()
        assertEquals("/data/app/com.inspect.testapp.apk", details.sourceDir)
        assertEquals("/data/user/0/com.inspect.testapp", details.dataDir)
        assertEquals(2, details.permissions.size)

        val internetPerm = details.permissions.find { it.name == "android.permission.INTERNET" }
        assertNotNull(internetPerm)
        assertTrue(internetPerm!!.isGranted)

        val cameraPerm = details.permissions.find { it.name == "android.permission.CAMERA" }
        assertNotNull(cameraPerm)
        assertFalse(cameraPerm!!.isGranted)

        assertEquals(1, details.componentCounts.activityCount)
        assertEquals(1, details.componentCounts.serviceCount)
        assertEquals(0, details.componentCounts.receiverCount)
        assertEquals(2, details.componentCounts.totalComponents)
    }

    @Test
    fun getAppDetails_returnsFailureForNonExistentPackage() = runTest(testDispatcher) {
        val result = repository.getAppDetails("com.nonexistent.fake.app")
        assertTrue(result.isFailure)
    }
}
