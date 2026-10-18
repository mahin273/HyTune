package com.mahin.hytune.domain.detection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IgnoredPackagesFilterTest {

    private lateinit var context: Context
    private lateinit var filter: IgnoredPackagesFilter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        filter = IgnoredPackagesFilter(context)
    }

    @Test
    fun isSystemUiOrOverlay_identifiesSystemUiAndKeyboards() {
        assertTrue(filter.isSystemUiOrOverlay("com.android.systemui"))
        assertTrue(filter.isSystemUiOrOverlay("com.google.android.inputmethod.latin"))
        assertTrue(filter.isSystemUiOrOverlay("com.touchtype.swiftkey"))
        assertTrue(filter.isSystemUiOrOverlay("com.samsung.android.honeyboard"))
        assertTrue(filter.isSystemUiOrOverlay(context.packageName))
    }

    @Test
    fun isLauncher_identifiesStandardLaunchers() {
        assertTrue(filter.isLauncher("com.google.android.apps.nexuslauncher"))
        assertTrue(filter.isLauncher("com.android.launcher3"))
        assertTrue(filter.isLauncher("ch.deletescape.lawnchair"))
        assertTrue(filter.isLauncher("com.teslacoilsw.launcher"))
    }

    @Test
    fun regularApps_areNotIgnored() {
        assertFalse(filter.isSystemUiOrOverlay("com.mojang.minecraftpe"))
        assertFalse(filter.isLauncher("com.mojang.minecraftpe"))
        assertFalse(filter.isIgnored("com.mojang.minecraftpe"))

        assertFalse(filter.isSystemUiOrOverlay("com.spotify.music"))
        assertFalse(filter.isLauncher("com.spotify.music"))
        assertFalse(filter.isIgnored("com.spotify.music"))
    }

    @Test
    fun nullOrBlankPackages_areIgnored() {
        assertTrue(filter.isSystemUiOrOverlay(null))
        assertTrue(filter.isSystemUiOrOverlay(""))
        assertTrue(filter.isSystemUiOrOverlay("   "))
        assertFalse(filter.isLauncher(null))
    }
}
