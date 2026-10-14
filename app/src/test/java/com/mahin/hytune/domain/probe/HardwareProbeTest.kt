package com.mahin.hytune.domain.probe

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.probe.model.ClusterType
import com.mahin.hytune.domain.probe.model.GpuVendor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HardwareProbeTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var probe: HardwareProbe

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        probe = HardwareProbe(privilegeBackend = fakeBackend)
    }

    @Test
    fun probeCpuInfo_parsesProcCpuinfoAccurately() = runTest {
        val cpuinfo = """
            processor	: 0
            BogoMIPS	: 38.40
            Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics
            CPU implementer	: 0x51
            CPU architecture: 8
            CPU variant	: 0xd
            CPU part	: 0x805
            CPU revision	: 14

            processor	: 1
            BogoMIPS	: 38.40
            Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics
            CPU implementer	: 0x51
            CPU architecture: 8
            CPU variant	: 0xd
            CPU part	: 0x805
            CPU revision	: 14

            Hardware	: Qualcomm Technologies, Inc SM8450
        """.trimIndent()

        fakeBackend.setFile("/proc/cpuinfo", cpuinfo)

        val summary = probe.probeCpuInfo()

        assertEquals(2, summary.coreCount)
        assertEquals("Qualcomm Technologies, Inc SM8450", summary.hardwareName)
        assertEquals("0x51", summary.implementer)
        assertEquals("8", summary.architecture)
    }

    @Test
    fun probeCpuClusters_triClusterSnapdragonTopology() = runTest {
        val policyBase = "/sys/devices/system/cpu/cpufreq"

        fakeBackend.setFile("$policyBase/policy0/affected_cpus", "0 1 2 3")
        fakeBackend.setFile("$policyBase/policy0/scaling_available_frequencies", "300000 576000 1804800")

        fakeBackend.setFile("$policyBase/policy4/affected_cpus", "4 5 6")
        fakeBackend.setFile("$policyBase/policy4/scaling_available_frequencies", "403200 1056000 2496000")

        fakeBackend.setFile("$policyBase/policy7/affected_cpus", "7")
        fakeBackend.setFile("$policyBase/policy7/scaling_available_frequencies", "806400 1785600 2995200")

        val clusters = probe.probeCpuClusters()

        assertEquals(3, clusters.size)

        val little = clusters[0]
        assertEquals(0, little.clusterId)
        assertEquals(listOf(0, 1, 2, 3), little.coreIds)
        assertEquals(ClusterType.LITTLE, little.type)
        assertEquals(300000L, little.minFreqKHz)
        assertEquals(1804800L, little.maxFreqKHz)

        val mid = clusters[1]
        assertEquals(4, mid.clusterId)
        assertEquals(listOf(4, 5, 6), mid.coreIds)
        assertEquals(ClusterType.MID, mid.type)
        assertEquals(403200L, mid.minFreqKHz)
        assertEquals(2496000L, mid.maxFreqKHz)

        val prime = clusters[2]
        assertEquals(7, prime.clusterId)
        assertEquals(listOf(7), prime.coreIds)
        assertEquals(ClusterType.PRIME, prime.type)
        assertEquals(806400L, prime.minFreqKHz)
        assertEquals(2995200L, prime.maxFreqKHz)
    }

    @Test
    fun probeCpuClusters_dualClusterMediaTekTopology() = runTest {
        val policyBase = "/sys/devices/system/cpu/cpufreq"

        fakeBackend.setFile("$policyBase/policy0/affected_cpus", "0 1 2 3")
        fakeBackend.setFile("$policyBase/policy0/scaling_available_frequencies", "500000 1000000 2000000")

        fakeBackend.setFile("$policyBase/policy4/affected_cpus", "4 5 6 7")
        fakeBackend.setFile("$policyBase/policy4/scaling_available_frequencies", "800000 1500000 2800000")

        val clusters = probe.probeCpuClusters()

        assertEquals(2, clusters.size)
        assertEquals(ClusterType.LITTLE, clusters[0].type)
        assertEquals(listOf(0, 1, 2, 3), clusters[0].coreIds)
        assertEquals(ClusterType.BIG, clusters[1].type)
        assertEquals(listOf(4, 5, 6, 7), clusters[1].coreIds)
    }

    @Test
    fun probeCpuClusters_fallbackToCpuDirsWhenPolicyDirMissing() = runTest {
        val cpuBase = "/sys/devices/system/cpu"

        fakeBackend.setFile("$cpuBase/cpu0/cpufreq/affected_cpus", "0 1")
        fakeBackend.setFile("$cpuBase/cpu0/cpufreq/scaling_available_frequencies", "400000 1200000")

        fakeBackend.setFile("$cpuBase/cpu2/cpufreq/affected_cpus", "2 3")
        fakeBackend.setFile("$cpuBase/cpu2/cpufreq/scaling_available_frequencies", "600000 1800000")

        val clusters = probe.probeCpuClusters()

        assertEquals(2, clusters.size)
        assertEquals(listOf(0, 1), clusters[0].coreIds)
        assertEquals(listOf(2, 3), clusters[1].coreIds)
        assertEquals(ClusterType.LITTLE, clusters[0].type)
        assertEquals(ClusterType.BIG, clusters[1].type)
    }

    @Test
    fun probeGpu_detectsAdrenoWithNormalizedFrequencies() = runTest {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "220000000 500000000 818000000")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance powersave")

        val gpu = probe.probeGpu()

        assertNotNull(gpu)
        assertEquals(GpuVendor.ADRENO, gpu?.vendor)
        assertEquals(listOf(220000L, 500000L, 818000L), gpu?.availableFrequenciesKHz)
        assertEquals("msm-adreno-tz", gpu?.currentGovernor)
        assertEquals(listOf("msm-adreno-tz", "performance", "powersave"), gpu?.availableGovernors)
        assertEquals(220000L, gpu?.minFreqKHz)
        assertEquals(818000L, gpu?.maxFreqKHz)
    }

    @Test
    fun probeGpu_detectsMaliDevfreq() = runTest {
        val mali = "/sys/class/devfreq/13000000.mali"
        fakeBackend.setFile("$mali/available_frequencies", "300000000 550000000 850000000")
        fakeBackend.setFile("$mali/governor", "simple_ondemand")
        fakeBackend.setFile("$mali/available_governors", "simple_ondemand performance")

        val gpu = probe.probeGpu()

        assertNotNull(gpu)
        assertEquals(GpuVendor.MALI, gpu?.vendor)
        assertEquals(listOf(300000L, 550000L, 850000L), gpu?.availableFrequenciesKHz)
        assertEquals("simple_ondemand", gpu?.currentGovernor)
        assertEquals(listOf("simple_ondemand", "performance"), gpu?.availableGovernors)
    }

    @Test
    fun probeGpu_returnsNullWhenNoGpuFound() = runTest {
        val gpu = probe.probeGpu()
        assertNull(gpu)
    }

    @Test
    fun probeHardware_combinesAllComponents() = runTest {
        fakeBackend.setFile("/proc/cpuinfo", "processor : 0\nHardware : Tensor G2")
        fakeBackend.setFile("/sys/devices/system/cpu/cpufreq/policy0/affected_cpus", "0 1 2 3")
        fakeBackend.setFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies", "300000 1800000")
        fakeBackend.setFile("/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies", "300000000 848000000")

        val summary = probe.probeHardware()

        assertEquals("Tensor G2", summary.socName)
        assertEquals(4, summary.totalCores)
        assertEquals(1, summary.clusters.size)
        assertEquals(ClusterType.BIG, summary.clusters[0].type)
        assertNotNull(summary.gpu)
        assertEquals(GpuVendor.ADRENO, summary.gpu?.vendor)
    }

    @Test
    fun probeHardware_emptyFilesystem_handlesDefensively() = runTest {
        val summary = probe.probeHardware()

        assertTrue(summary.socName.isNotBlank())
        assertTrue(summary.totalCores > 0)
        assertTrue(summary.clusters.isEmpty())
        assertNull(summary.gpu)
    }
}
