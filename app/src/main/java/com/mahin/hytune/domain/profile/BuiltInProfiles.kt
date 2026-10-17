package com.mahin.hytune.domain.profile

import com.mahin.hytune.domain.profile.model.CpuClusterProfileConfig
import com.mahin.hytune.domain.profile.model.FrequencyTarget
import com.mahin.hytune.domain.profile.model.GpuProfileConfig
import com.mahin.hytune.domain.profile.model.MemoryProfileConfig
import com.mahin.hytune.domain.profile.model.ProfileMode
import com.mahin.hytune.domain.profile.model.StorageProfileConfig
import com.mahin.hytune.domain.profile.model.TuningProfile

object BuiltInProfiles {

    val POWERSAVE = TuningProfile(
        id = "powersave",
        name = "Powersave",
        description = "Conserves battery by throttling maximum frequencies and prioritizing energy efficiency.",
        mode = ProfileMode.POWERSAVE,
        isBuiltIn = true,
        cpuClusters = mapOf(
            0 to CpuClusterProfileConfig(
                clusterId = 0,
                governor = "powersave",
                minFreqTarget = FrequencyTarget.Percentage(0.0f),
                maxFreqTarget = FrequencyTarget.Percentage(0.5f)
            ),
            1 to CpuClusterProfileConfig(
                clusterId = 1,
                governor = "powersave",
                minFreqTarget = FrequencyTarget.Percentage(0.0f),
                maxFreqTarget = FrequencyTarget.Percentage(0.5f)
            ),
            2 to CpuClusterProfileConfig(
                clusterId = 2,
                governor = "powersave",
                minFreqTarget = FrequencyTarget.Percentage(0.0f),
                maxFreqTarget = FrequencyTarget.Percentage(0.5f)
            )
        ),
        gpu = GpuProfileConfig(
            governor = "powersave",
            minFreqTarget = FrequencyTarget.Percentage(0.0f),
            maxFreqTarget = FrequencyTarget.Percentage(0.4f)
        ),
        memory = MemoryProfileConfig(
            swappiness = 40,
            vfsCachePressure = 120,
            dropCachesBeforeApply = false
        ),
        storage = StorageProfileConfig(
            scheduler = "mq-deadline",
            readAheadKb = 128,
            runTrimBeforeApply = false
        )
    )

    val BALANCE = TuningProfile(
        id = "balance",
        name = "Balance",
        description = "Harmonious daily driver configuration balancing snappy performance with thermal and battery economy.",
        mode = ProfileMode.BALANCE,
        isBuiltIn = true,
        cpuClusters = mapOf(
            0 to CpuClusterProfileConfig(
                clusterId = 0,
                governor = "schedutil",
                minFreqTarget = FrequencyTarget.Percentage(0.0f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            ),
            1 to CpuClusterProfileConfig(
                clusterId = 1,
                governor = "schedutil",
                minFreqTarget = FrequencyTarget.Percentage(0.0f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            ),
            2 to CpuClusterProfileConfig(
                clusterId = 2,
                governor = "schedutil",
                minFreqTarget = FrequencyTarget.Percentage(0.0f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            )
        ),
        gpu = GpuProfileConfig(
            governor = "msm-adreno-tz",
            minFreqTarget = FrequencyTarget.Percentage(0.0f),
            maxFreqTarget = FrequencyTarget.Percentage(1.0f)
        ),
        memory = MemoryProfileConfig(
            swappiness = 80,
            vfsCachePressure = 100,
            dropCachesBeforeApply = false
        ),
        storage = StorageProfileConfig(
            scheduler = "mq-deadline",
            readAheadKb = 512,
            runTrimBeforeApply = false
        )
    )

    val PERFORMANCE = TuningProfile(
        id = "performance",
        name = "Performance",
        description = "Accelerates CPU/GPU clock ramping and boosts filesystem cache retention for high responsiveness.",
        mode = ProfileMode.PERFORMANCE,
        isBuiltIn = true,
        cpuClusters = mapOf(
            0 to CpuClusterProfileConfig(
                clusterId = 0,
                governor = "performance",
                minFreqTarget = FrequencyTarget.Percentage(0.4f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            ),
            1 to CpuClusterProfileConfig(
                clusterId = 1,
                governor = "performance",
                minFreqTarget = FrequencyTarget.Percentage(0.5f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            ),
            2 to CpuClusterProfileConfig(
                clusterId = 2,
                governor = "performance",
                minFreqTarget = FrequencyTarget.Percentage(0.6f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            )
        ),
        gpu = GpuProfileConfig(
            governor = "performance",
            minFreqTarget = FrequencyTarget.Percentage(0.5f),
            maxFreqTarget = FrequencyTarget.Percentage(1.0f)
        ),
        memory = MemoryProfileConfig(
            swappiness = 120,
            vfsCachePressure = 80,
            dropCachesBeforeApply = false
        ),
        storage = StorageProfileConfig(
            scheduler = "none",
            readAheadKb = 1024,
            runTrimBeforeApply = false
        )
    )

    val FAST = TuningProfile(
        id = "fast",
        name = "Fast",
        description = "Extreme gaming and benchmark configuration with maximum sustained clocks and direct hardware I/O dispatch.",
        mode = ProfileMode.FAST,
        isBuiltIn = true,
        cpuClusters = mapOf(
            0 to CpuClusterProfileConfig(
                clusterId = 0,
                governor = "performance",
                minFreqTarget = FrequencyTarget.Percentage(1.0f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            ),
            1 to CpuClusterProfileConfig(
                clusterId = 1,
                governor = "performance",
                minFreqTarget = FrequencyTarget.Percentage(1.0f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            ),
            2 to CpuClusterProfileConfig(
                clusterId = 2,
                governor = "performance",
                minFreqTarget = FrequencyTarget.Percentage(1.0f),
                maxFreqTarget = FrequencyTarget.Percentage(1.0f)
            )
        ),
        gpu = GpuProfileConfig(
            governor = "performance",
            minFreqTarget = FrequencyTarget.Percentage(1.0f),
            maxFreqTarget = FrequencyTarget.Percentage(1.0f)
        ),
        memory = MemoryProfileConfig(
            swappiness = 150,
            vfsCachePressure = 50,
            dropCachesBeforeApply = true
        ),
        storage = StorageProfileConfig(
            scheduler = "none",
            readAheadKb = 2048,
            runTrimBeforeApply = true
        )
    )

    val ALL_BUILT_IN = listOf(POWERSAVE, BALANCE, PERFORMANCE, FAST)

    fun findById(id: String): TuningProfile? {
        return ALL_BUILT_IN.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
