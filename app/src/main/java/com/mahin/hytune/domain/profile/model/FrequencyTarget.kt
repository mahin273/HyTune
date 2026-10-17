package com.mahin.hytune.domain.profile.model

sealed interface FrequencyTarget {
    data class Percentage(val percent: Float) : FrequencyTarget {
        init {
            require(percent in 0.0f..1.0f) { "Percentage target must be between 0.0 and 1.0, got $percent" }
        }
    }

    data class AbsoluteKHz(val freqKHz: Long) : FrequencyTarget {
        init {
            require(freqKHz >= 0L) { "Frequency in kHz must be non-negative, got $freqKHz" }
        }
    }

    enum class Step : FrequencyTarget {
        MIN,
        MID,
        MAX
    }
}
