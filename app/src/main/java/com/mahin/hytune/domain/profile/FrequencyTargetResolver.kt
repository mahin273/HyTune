package com.mahin.hytune.domain.profile

import com.mahin.hytune.domain.profile.model.FrequencyTarget
import kotlin.math.abs
import kotlin.math.roundToInt

object FrequencyTargetResolver {

    fun resolve(target: FrequencyTarget, availableSteps: List<Long>): Long {
        if (availableSteps.isEmpty()) return 0L
        if (availableSteps.size == 1) return availableSteps.first()

        val sorted = availableSteps.distinct().sorted()

        return when (target) {
            is FrequencyTarget.Percentage -> {
                val clamped = target.percent.coerceIn(0.0f, 1.0f)
                val index = (clamped * (sorted.size - 1)).roundToInt()
                sorted[index]
            }

            is FrequencyTarget.Step -> {
                when (target) {
                    FrequencyTarget.Step.MIN -> sorted.first()
                    FrequencyTarget.Step.MID -> sorted[sorted.size / 2]
                    FrequencyTarget.Step.MAX -> sorted.last()
                }
            }

            is FrequencyTarget.AbsoluteKHz -> {
                sorted.minByOrNull { abs(it - target.freqKHz) } ?: target.freqKHz
            }
        }
    }
}
