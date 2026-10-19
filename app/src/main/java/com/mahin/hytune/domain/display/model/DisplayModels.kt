package com.mahin.hytune.domain.display.model

data class DisplayResolution(
    val width: Int,
    val height: Int
) {
    override fun toString(): String = "${width}x${height}"

    val aspectRatio: Float
        get() = if (width > 0) height.toFloat() / width.toFloat() else 0f
}

data class DisplayDensity(
    val dpi: Int
)

data class DisplayRefreshRate(
    val minHz: Float,
    val peakHz: Float
) {
    val isLocked: Boolean
        get() = minHz == peakHz
}

data class DisplayStateSnapshot(
    val physicalWidth: Int,
    val physicalHeight: Int,
    val overrideWidth: Int? = null,
    val overrideHeight: Int? = null,
    val physicalDpi: Int,
    val overrideDpi: Int? = null,
    val minRefreshRateHz: Float? = null,
    val peakRefreshRateHz: Float? = null
) {
    val activeWidth: Int
        get() = overrideWidth ?: physicalWidth

    val activeHeight: Int
        get() = overrideHeight ?: physicalHeight

    val activeDpi: Int
        get() = overrideDpi ?: physicalDpi

    val isOverridden: Boolean
        get() = overrideWidth != null || overrideHeight != null || overrideDpi != null

    val activeResolutionString: String
        get() = "${activeWidth}x${activeHeight}"

    val physicalResolutionString: String
        get() = "${physicalWidth}x${physicalHeight}"
}

data class DisplayConfig(
    val resolution: DisplayResolution? = null,
    val density: DisplayDensity? = null,
    val refreshRate: DisplayRefreshRate? = null
)

data class DisplayPreset(
    val label: String,
    val scaleFactor: Float
)
