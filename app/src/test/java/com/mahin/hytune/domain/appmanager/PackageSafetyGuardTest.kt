package com.mahin.hytune.domain.appmanager

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
@Config(manifest = Config.NONE, sdk = [33])
class PackageSafetyGuardTest {

    private lateinit var context: Context
    private lateinit var guard: PackageSafetyGuard

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        guard = PackageSafetyGuard(context)
    }

    @Test
    fun isCritical_returnsTrue_forKnownCriticalPackages() {
        assertTrue(guard.isCritical("android"))
        assertTrue(guard.isCritical("com.android.systemui"))
        assertTrue(guard.isCritical("com.android.settings"))
        assertTrue(guard.isCritical("com.android.phone"))
        assertTrue(guard.isCritical("com.google.android.gms"))
        assertTrue(guard.isCritical("com.google.android.gsf"))
        assertTrue(guard.isCritical("com.mahin.hytune"))
        assertTrue(guard.isCritical(context.packageName))
    }

    @Test
    fun isCritical_returnsFalse_forRegularPackages() {
        assertFalse(guard.isCritical("com.spotify.music"))
        assertFalse(guard.isCritical("com.facebook.katana"))
        assertFalse(guard.isCritical("com.instagram.android"))
    }

    @Test(expected = SecurityException::class)
    fun validateOperation_throws_whenModifyingSelf() {
        guard.validateOperation(context.packageName, isDestructive = true)
    }

    @Test(expected = SecurityException::class)
    fun validateOperation_throws_whenDestructiveOnCriticalPackage() {
        guard.validateOperation("com.android.systemui", isDestructive = true)
    }

    @Test
    fun validateOperation_passes_forNonDestructiveOperationOnCriticalPackage() {
        guard.validateOperation("com.android.settings", isDestructive = false)
    }

    @Test
    fun validateOperation_passes_forRegularPackage() {
        guard.validateOperation("com.spotify.music", isDestructive = true)
    }
}
