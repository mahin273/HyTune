package com.mahin.hytune.ui.navigation

import androidx.compose.ui.text.font.FontFamily
import com.mahin.hytune.ui.theme.AmoledBlack
import com.mahin.hytune.ui.theme.DarkColorScheme
import com.mahin.hytune.ui.theme.MonospaceCodeStyle
import com.mahin.hytune.ui.theme.Typography
import com.mahin.hytune.ui.theme.createAmoledColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationTest {

    @Test
    fun testDestinationsHaveUniqueRoutes() {
        val destinations = HyTuneDestination.bottomNavDestinations
        assertEquals(5, destinations.size)

        val uniqueRoutes = destinations.map { it.route }.toSet()
        assertEquals(5, uniqueRoutes.size)
    }

    @Test
    fun testDestinationProperties() {
        assertEquals("home", HyTuneDestination.Home.route)
        assertEquals("Home", HyTuneDestination.Home.label)
        assertNotNull(HyTuneDestination.Home.icon)

        assertEquals("tune", HyTuneDestination.Tune.route)
        assertEquals("Tune", HyTuneDestination.Tune.label)
        assertNotNull(HyTuneDestination.Tune.icon)

        assertEquals("apps", HyTuneDestination.Apps.route)
        assertEquals("Apps", HyTuneDestination.Apps.label)
        assertNotNull(HyTuneDestination.Apps.icon)

        assertEquals("monitor", HyTuneDestination.Monitor.route)
        assertEquals("Monitor", HyTuneDestination.Monitor.label)
        assertNotNull(HyTuneDestination.Monitor.icon)

        assertEquals("more", HyTuneDestination.More.route)
        assertEquals("More", HyTuneDestination.More.label)
        assertNotNull(HyTuneDestination.More.icon)
    }

    @Test
    fun testFromRouteResolvesCorrectDestination() {
        assertEquals(HyTuneDestination.Home, HyTuneDestination.fromRoute("home"))
        assertEquals(HyTuneDestination.Tune, HyTuneDestination.fromRoute("tune"))
        assertEquals(HyTuneDestination.Apps, HyTuneDestination.fromRoute("apps"))
        assertEquals(HyTuneDestination.Monitor, HyTuneDestination.fromRoute("monitor"))
        assertEquals(HyTuneDestination.More, HyTuneDestination.fromRoute("more"))
    }

    @Test
    fun testFromRouteDefaultsToHomeOnUnknownOrNull() {
        assertEquals(HyTuneDestination.Home, HyTuneDestination.fromRoute(null))
        assertEquals(HyTuneDestination.Home, HyTuneDestination.fromRoute(""))
        assertEquals(HyTuneDestination.Home, HyTuneDestination.fromRoute("unknown_route"))
    }

    @Test
    fun testAmoledColorSchemeSetsPureBlackSurfaces() {
        val amoledScheme = createAmoledColorScheme(DarkColorScheme)
        assertEquals(AmoledBlack, amoledScheme.background)
        assertEquals(AmoledBlack, amoledScheme.surface)
        assertEquals(AmoledBlack, amoledScheme.surfaceContainerLow)
        assertEquals(AmoledBlack, amoledScheme.surfaceContainerLowest)
    }

    @Test
    fun testTypographyIncludesMonospaceStyles() {
        assertEquals(FontFamily.Monospace, Typography.labelSmall.fontFamily)
        assertEquals(FontFamily.Monospace, MonospaceCodeStyle.fontFamily)
    }
}
