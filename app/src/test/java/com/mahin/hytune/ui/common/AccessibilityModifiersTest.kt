package com.mahin.hytune.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityModifiersTest {

    private fun Modifier.extractSemanticsConfigurations(): List<SemanticsConfiguration> {
        val configs = mutableListOf<SemanticsConfiguration>()
        this.foldIn(Unit) { _, element ->
            if (element is SemanticsModifier) {
                configs.add(element.semanticsConfiguration)
            }
        }
        return configs
    }

    @Test
    fun testTelemetryAnnouncementSemantics() {
        val modifier = Modifier.telemetryAnnouncement("CPU Load", "45%")
        val configs = modifier.extractSemanticsConfigurations()

        assertEquals(1, configs.size)
        val config = configs.first()
        assertTrue(config.isMergingSemanticsOfDescendants)
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)
        assertNotNull(contentDesc)
        assertEquals(listOf("CPU Load: 45%"), contentDesc)
    }

    @Test
    fun testProfileSemanticsActive() {
        val modifier = Modifier.profileSemantics("Performance", isSelected = true)
        val configs = modifier.extractSemanticsConfigurations()

        assertEquals(1, configs.size)
        val config = configs.first()
        val role = config.getOrNull(SemanticsProperties.Role)
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)
        val stateDesc = config.getOrNull(SemanticsProperties.StateDescription)

        assertEquals(Role.RadioButton, role)
        assertEquals(listOf("Performance profile"), contentDesc)
        assertEquals("Active profile", stateDesc)
    }

    @Test
    fun testProfileSemanticsInactive() {
        val modifier = Modifier.profileSemantics("PowerSave", isSelected = false)
        val configs = modifier.extractSemanticsConfigurations()

        assertEquals(1, configs.size)
        val config = configs.first()
        val role = config.getOrNull(SemanticsProperties.Role)
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)
        val stateDesc = config.getOrNull(SemanticsProperties.StateDescription)

        assertEquals(Role.RadioButton, role)
        assertEquals(listOf("PowerSave profile"), contentDesc)
        assertEquals("Available profile", stateDesc)
    }

    @Test
    fun testToggleSemantics() {
        val modifier = Modifier.toggleSemantics("Performance Overlay", isEnabled = true) {}
        val configs = modifier.extractSemanticsConfigurations()

        assertEquals(1, configs.size)
        val config = configs.first()
        val role = config.getOrNull(SemanticsProperties.Role)
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)
        val stateDesc = config.getOrNull(SemanticsProperties.StateDescription)

        assertEquals(Role.Switch, role)
        assertEquals(listOf("Performance Overlay"), contentDesc)
        assertEquals("Enabled", stateDesc)
    }

    @Test
    fun testLoadingSemantics() {
        val modifier = Modifier.loadingSemantics("Loading hardware telemetry")
        val configs = modifier.extractSemanticsConfigurations()

        assertEquals(1, configs.size)
        val config = configs.first()
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Loading hardware telemetry"), contentDesc)
    }

    @Test
    fun testTalkBackActionSemantics() {
        val modifier = Modifier.talkBackAction("Apply Settings", Role.Button) {}
        val configs = modifier.extractSemanticsConfigurations()

        assertEquals(1, configs.size)
        val config = configs.first()
        val role = config.getOrNull(SemanticsProperties.Role)
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)

        assertEquals(Role.Button, role)
        assertEquals(listOf("Apply Settings"), contentDesc)
    }
}
