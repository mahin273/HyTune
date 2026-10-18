package com.mahin.hytune.data.local.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.entity.FreezeGroupEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupPackageCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class FreezeGroupDaoTest {

    private lateinit var db: HyTuneDatabase
    private lateinit var dao: FreezeGroupDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.freezeGroupDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryGroupWithPackages() = runBlocking {
        val groupId = dao.insertGroup(
            FreezeGroupEntity(
                name = "Social",
                description = "Social Media Apps",
                autoFreezeOnScreenOff = true,
                autoFreezeDelaySeconds = 15
            )
        )

        dao.insertPackageCrossRef(FreezeGroupPackageCrossRef(groupId, "com.facebook.katana"))
        dao.insertPackageCrossRef(FreezeGroupPackageCrossRef(groupId, "com.instagram.android"))

        val groups = dao.getAllGroupsWithPackages().first()
        assertEquals(1, groups.size)
        assertEquals("Social", groups[0].group.name)
        assertEquals(2, groups[0].packages.size)
        assertTrue(groups[0].packages.any { it.packageName == "com.facebook.katana" })
        assertTrue(groups[0].packages.any { it.packageName == "com.instagram.android" })
    }

    @Test
    fun getAutoFreezeGroups_returnsOnlyGroupsWithAutoFreezeTrue() = runBlocking {
        val group1 = dao.insertGroup(
            FreezeGroupEntity(name = "Group1", autoFreezeOnScreenOff = true)
        )
        val group2 = dao.insertGroup(
            FreezeGroupEntity(name = "Group2", autoFreezeOnScreenOff = false)
        )

        dao.insertPackageCrossRef(FreezeGroupPackageCrossRef(group1, "com.app1"))
        dao.insertPackageCrossRef(FreezeGroupPackageCrossRef(group2, "com.app2"))

        val autoGroups = dao.getAutoFreezeGroups()
        assertEquals(1, autoGroups.size)
        assertEquals("Group1", autoGroups[0].group.name)
    }

    @Test
    fun deleteGroup_cascadesAndDeletesPackages() = runBlocking {
        val groupId = dao.insertGroup(
            FreezeGroupEntity(name = "To Delete")
        )
        dao.insertPackageCrossRef(FreezeGroupPackageCrossRef(groupId, "com.pkg1"))
        dao.insertPackageCrossRef(FreezeGroupPackageCrossRef(groupId, "com.pkg2"))

        val before = dao.getGroupWithPackagesById(groupId).first()
        assertNotNull(before)
        assertEquals(2, before!!.packages.size)

        dao.deleteGroup(groupId)

        val after = dao.getGroupWithPackagesById(groupId).first()
        assertNull(after)
    }

    @Test
    fun setGroupPackages_replacesExistingPackages() = runBlocking {
        val groupId = dao.insertGroup(FreezeGroupEntity(name = "Games"))
        dao.setGroupPackages(groupId, listOf("com.game1", "com.game2"))

        val firstState = dao.getGroupWithPackagesByIdSync(groupId)
        assertNotNull(firstState)
        assertEquals(2, firstState!!.packages.size)

        dao.setGroupPackages(groupId, listOf("com.game3"))
        val secondState = dao.getGroupWithPackagesByIdSync(groupId)
        assertNotNull(secondState)
        assertEquals(1, secondState!!.packages.size)
        assertEquals("com.game3", secondState.packages[0].packageName)
    }
}
