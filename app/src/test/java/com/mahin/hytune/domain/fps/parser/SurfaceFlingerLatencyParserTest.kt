package com.mahin.hytune.domain.fps.parser

import com.mahin.hytune.domain.fps.model.FpsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SurfaceFlingerLatencyParserTest {

    private lateinit var parser: SurfaceFlingerLatencyParser

    @Before
    fun setUp() {
        parser = SurfaceFlingerLatencyParser()
    }

    @Test
    fun parse_withEmptyOrShortOutput_returnsZeroFps() {
        val result = parser.parse("", packageName = "com.example.game")
        assertEquals(0.0, result.snapshot.fps, 0.01)
        assertEquals(0, result.snapshot.totalFrames)
        assertEquals(FpsSource.SURFACE_FLINGER, result.snapshot.source)
        assertEquals("com.example.game", result.snapshot.packageName)
    }

    @Test
    fun parse_with60HzSmoothFrames_calculates60FpsAndNoJank() {
        // Line 0: 16.666ms (16666666 ns)
        // 60 consecutive frames with 16.666ms delta = exactly 1 second elapsed
        val sb = StringBuilder("16666666\n")
        var currentTs = 1_000_000_000L
        val interval = 16_666_666L
        for (i in 0 until 60) {
            val desired = currentTs
            val actual = currentTs + 1_000_000L
            val ready = currentTs + 500_000L
            sb.append("$desired\t$actual\t$ready\n")
            currentTs += interval
        }

        val result = parser.parse(sb.toString(), packageName = "com.test.game")
        assertEquals(60, result.snapshot.targetFps)
        assertEquals(60, result.snapshot.totalFrames)
        assertEquals(0, result.snapshot.jankCount)
        assertEquals(0.0, result.snapshot.jankPercentage, 0.01)
        assertTrue(result.snapshot.fps in 59.0..61.0)
        assertTrue(result.latestTimestampNs > 0)
    }

    @Test
    fun parse_filtersOutSentinelsAndUnpresentedFrames() {
        val raw = """
            16666666
            1000000000	1016666666	1015000000
            1016666666	0	1030000000
            1033333333	9223372036854775807	1045000000
            1050000000	1033333332	1060000000
        """.trimIndent()

        val result = parser.parse(raw, packageName = "com.test.app")
        assertEquals(2, result.snapshot.totalFrames)
        assertEquals(1033333332L, result.latestTimestampNs)
    }

    @Test
    fun parse_with120HzAndStutters_detectsJankFrames() {
        // 120 Hz = 8.333ms per frame
        // Jank threshold = 1.5 * 8.333ms = 12.5ms
        val raw = """
            8333333
            1000000000	1008333333	1005000000
            1008333333	1016666666	1015000000
            1016666666	1040000000	1038000000
            1040000000	1048333333	1045000000
        """.trimIndent()

        val result = parser.parse(raw, packageName = "com.test.highrefresh")
        assertEquals(120, result.snapshot.targetFps)
        assertEquals(4, result.snapshot.totalFrames)
        // Frame 3 has delta: 1040000000 - 1016666666 = 23.33ms (> 12.5ms jank threshold)
        assertEquals(1, result.snapshot.jankCount)
        assertEquals(25.0, result.snapshot.jankPercentage, 0.5)
    }

    @Test
    fun parse_withIncrementalTimestampTracking_onlyProcessesNewFrames() {
        val rawBatch1 = """
            16666666
            1000000000	1016666666	1015000000
            1016666666	1033333332	1030000000
        """.trimIndent()

        val result1 = parser.parse(rawBatch1, lastSeenTimestampNs = 0L)
        assertEquals(2, result1.snapshot.totalFrames)
        assertEquals(1033333332L, result1.latestTimestampNs)

        val rawBatch2 = """
            16666666
            1000000000	1016666666	1015000000
            1016666666	1033333332	1030000000
            1033333332	1049999998	1045000000
            1049999998	1066666664	1060000000
        """.trimIndent()

        val result2 = parser.parse(rawBatch2, lastSeenTimestampNs = result1.latestTimestampNs)
        // Only the 2 new frames should be processed
        assertEquals(2, result2.snapshot.totalFrames)
        assertEquals(1066666664L, result2.latestTimestampNs)
    }
}
