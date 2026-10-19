package com.mahin.hytune.domain.fps.parser

import com.mahin.hytune.domain.fps.model.FpsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GfxinfoFramestatsParserTest {

    private lateinit var parser: GfxinfoFramestatsParser

    @Before
    fun setUp() {
        parser = GfxinfoFramestatsParser()
    }

    @Test
    fun parse_withEmptyOrCorruptData_returnsEmptySnapshot() {
        val snapshot = parser.parse("No profile data here", packageName = "com.pkg")
        assertEquals(0.0, snapshot.fps, 0.01)
        assertEquals(0, snapshot.totalFrames)
        assertEquals(FpsSource.GFXINFO, snapshot.source)
    }

    @Test
    fun parse_withValidProfileData_calculatesMetricsCorrectly() {
        val raw = """
            Applications Graphics Acceleration Info:
            ---PROFILEDATA---
            Flags,IntendedVsync,Vsync,OldestInputEvent,NewestInputEvent,HandleInputStart,AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted
            0,1000000000,1000000000,0,0,1001000000,1002000000,1003000000,1004000000,1005000000,1006000000,1007000000,1008000000,1012000000
            0,1016666666,1016666666,0,0,1017000000,1018000000,1019000000,1020000000,1021000000,1022000000,1023000000,1024000000,1028000000
            0,1033333333,1033333333,0,0,1034000000,1035000000,1036000000,1037000000,1038000000,1039000000,1040000000,1041000000,1065000000
            ---PROFILEDATA---
        """.trimIndent()

        val snapshot = parser.parse(raw, packageName = "com.sample.app")
        assertEquals(3, snapshot.totalFrames)
        assertEquals(FpsSource.GFXINFO, snapshot.source)
        assertEquals("com.sample.app", snapshot.packageName)

        // Frame 1: 12ms duration (smooth)
        // Frame 2: 11.33ms duration (smooth)
        // Frame 3: 31.67ms duration (exceeds 25ms threshold -> jank!)
        assertEquals(1, snapshot.jankCount)
        assertEquals(33.3, snapshot.jankPercentage, 0.5)
        assertTrue(snapshot.frameTimeMs > 0.0)
    }

    @Test
    fun parse_filtersOutNonZeroFlagsRows() {
        val raw = """
            ---PROFILEDATA---
            Flags,IntendedVsync,Vsync,OldestInputEvent,NewestInputEvent,HandleInputStart,AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted
            1,1000000000,1000000000,0,0,1001000000,1002000000,1003000000,1004000000,1005000000,1006000000,1007000000,1008000000,1012000000
            0,1016666666,1016666666,0,0,1017000000,1018000000,1019000000,1020000000,1021000000,1022000000,1023000000,1024000000,1028000000
            ---PROFILEDATA---
        """.trimIndent()

        val snapshot = parser.parse(raw, packageName = "com.sample.app")
        // Only row with Flags == 0 should be parsed
        assertEquals(1, snapshot.totalFrames)
    }
}
