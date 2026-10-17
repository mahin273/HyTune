package com.mahin.hytune.domain.memory.model

data class ZramInfo(
    val enabled: Boolean,
    val disksizeBytes: Long,
    val origSizeBytes: Long,
    val comprSizeBytes: Long,
    val memUsedTotalBytes: Long,
    val activeAlgorithm: String,
    val availableAlgorithms: List<String>,
    val maxCompStreams: Int = 1
) {
    val compressionRatio: Float
        get() = if (comprSizeBytes > 0L) {
            origSizeBytes.toFloat() / comprSizeBytes.toFloat()
        } else {
            1.0f
        }

    val spaceSavingsPercent: Int
        get() = if (origSizeBytes > 0L && origSizeBytes >= comprSizeBytes) {
            (((origSizeBytes - comprSizeBytes).toFloat() / origSizeBytes.toFloat()) * 100f).toInt()
        } else {
            0
        }
}
