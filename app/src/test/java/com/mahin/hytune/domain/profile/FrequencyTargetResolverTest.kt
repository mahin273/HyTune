package com.mahin.hytune.domain.profile

import com.mahin.hytune.domain.profile.model.FrequencyTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyTargetResolverTest {

    private val sampleFrequencies = listOf(
        300000L,
        576000L,
        844800L,
        1113600L,
        1401600L,
        1804800L
    )

    @Test
    fun resolve_percentage0ReturnsMinimumStep() {
        val resolved = FrequencyTargetResolver.resolve(
            FrequencyTarget.Percentage(0.0f),
            sampleFrequencies
        )
        assertEquals(300000L, resolved)
    }

    @Test
    fun resolve_percentage100ReturnsMaximumStep() {
        val resolved = FrequencyTargetResolver.resolve(
            FrequencyTarget.Percentage(1.0f),
            sampleFrequencies
        )
        assertEquals(1804800L, resolved)
    }

    @Test
    fun resolve_percentage50ReturnsMidStep() {
        val resolved = FrequencyTargetResolver.resolve(
            FrequencyTarget.Percentage(0.5f),
            sampleFrequencies
        )
        // 0.5 * 5 = 2.5 -> round = 3 -> index 3 is 1113600L
        assertEquals(1113600L, resolved)
    }

    @Test
    fun resolve_stepMinMidMaxCorrectly() {
        assertEquals(300000L, FrequencyTargetResolver.resolve(FrequencyTarget.Step.MIN, sampleFrequencies))
        assertEquals(1113600L, FrequencyTargetResolver.resolve(FrequencyTarget.Step.MID, sampleFrequencies))
        assertEquals(1804800L, FrequencyTargetResolver.resolve(FrequencyTarget.Step.MAX, sampleFrequencies))
    }

    @Test
    fun resolve_absoluteSnapsToClosestHardwareStep() {
        // Target 900,000 kHz -> closest is 844,800 kHz (diff 55,200) vs 1,113,600 (diff 213,600)
        val resolved = FrequencyTargetResolver.resolve(
            FrequencyTarget.AbsoluteKHz(900000L),
            sampleFrequencies
        )
        assertEquals(844800L, resolved)

        // Target 2,000,000 kHz (overclock request) -> snaps to max 1,804,800 kHz
        val resolvedMax = FrequencyTargetResolver.resolve(
            FrequencyTarget.AbsoluteKHz(2000000L),
            sampleFrequencies
        )
        assertEquals(1804800L, resolvedMax)
    }

    @Test
    fun resolve_handlesSingleFrequencyStepGracefully() {
        val single = listOf(1500000L)
        assertEquals(1500000L, FrequencyTargetResolver.resolve(FrequencyTarget.Percentage(0.0f), single))
        assertEquals(1500000L, FrequencyTargetResolver.resolve(FrequencyTarget.Percentage(1.0f), single))
        assertEquals(1500000L, FrequencyTargetResolver.resolve(FrequencyTarget.Step.MID, single))
    }

    @Test
    fun resolve_returnsZeroWhenStepListIsEmpty() {
        assertEquals(0L, FrequencyTargetResolver.resolve(FrequencyTarget.Percentage(0.5f), emptyList()))
    }
}
