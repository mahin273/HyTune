package com.mahin.hytune.domain.overlay.model

data class OverlayConfig(
    val enabled: Boolean = false,
    val isCompact: Boolean = true,
    val opacity: Float = 0.85f,
    val showCpu: Boolean = true,
    val showGpu: Boolean = true,
    val showBattery: Boolean = true,
    val showTemp: Boolean = true,
    val showRam: Boolean = true,
    val showFps: Boolean = true,
    val positionX: Int = 100,
    val positionY: Int = 200
)
