package com.mahin.hytune.domain.diagnostics.engine

import android.content.Context
import android.hardware.SensorManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.battery.BatteryEngine
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.ChargingSessionTracker
import com.mahin.hytune.domain.diagnostics.model.DiagnosticStatus
import com.mahin.hytune.domain.diagnostics.model.HardwareSubsystem
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.thermal.ThermalEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = com.mahin.hytune.HyTuneApp::class)
class HardwareDiagnosticsEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var db: HyTuneDatabase
    private lateinit var batteryRepository: BatteryRepository
    private lateinit var sensorDiagnosticsEngine: SensorDiagnosticsEngine
    private lateinit var hardwareDiagnosticsEngine: HardwareDiagnosticsEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )

        // Setup CPU files
        fakeBackend.setFile("/sys/devices/system/cpu/online", "0-7\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", "schedutil\n")

        // Setup Thermal files
        fakeBackend.setFile("/sys/class/thermal/thermal_zone0/type", "cpu-thermal\n")
        fakeBackend.setFile("/sys/class/thermal/thermal_zone0/temp", "38000\n")

        // Setup Memory files
        fakeBackend.setFile(
            "/proc/meminfo",
            """
            MemTotal:        8000000 kB
            MemFree:         2000000 kB
            MemAvailable:    4000000 kB
            """.trimIndent()
        )
        fakeBackend.setFile("/proc/sys/vm/swappiness", "60\n")

        // Setup Battery sysfs
        fakeBackend.setFile("/sys/class/power_supply/battery/capacity", "85\n")
        fakeBackend.setFile("/sys/class/power_supply/battery/status", "Discharging\n")
        fakeBackend.setFile("/sys/class/power_supply/battery/health", "Good\n")
        fakeBackend.setFile("/sys/class/power_supply/battery/voltage_now", "4100000\n")
        fakeBackend.setFile("/sys/class/power_supply/battery/temp", "320\n")

        // Setup Battery engine
        val batteryEngine = BatteryEngine(context, fakeBackend, testDispatcher)
        val tracker = ChargingSessionTracker(context, db.chargingSessionDao(), batteryEngine, testDispatcher)
        batteryRepository = BatteryRepository(batteryEngine, db.chargingSessionDao(), tracker, testDispatcher)

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorDiagnosticsEngine = SensorDiagnosticsEngine(sensorManager)

        val probe = HardwareProbe(fakeBackend)
        val thermalEngine = ThermalEngine(fakeBackend, testDispatcher)

        hardwareDiagnosticsEngine = HardwareDiagnosticsEngine(
            backend = fakeBackend,
            probe = probe,
            thermalEngine = thermalEngine,
            batteryRepository = batteryRepository,
            sensorDiagnosticsEngine = sensorDiagnosticsEngine,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun runFullDiagnostics_returnsPass_whenAllSubsystemsHealthy() = runTest(testDispatcher) {
        val report = hardwareDiagnosticsEngine.runFullDiagnostics()
        assertNotNull(report)
        assertEquals(DiagnosticStatus.PASS, report.overallStatus)
        assertEquals(5, report.subsystems.size)

        val cpu = report.subsystems.first { it.subsystem == HardwareSubsystem.CPU }
        assertEquals(DiagnosticStatus.PASS, cpu.status)

        val thermal = report.subsystems.first { it.subsystem == HardwareSubsystem.THERMAL }
        assertEquals(DiagnosticStatus.PASS, thermal.status)

        val memory = report.subsystems.first { it.subsystem == HardwareSubsystem.MEMORY }
        assertEquals(DiagnosticStatus.PASS, memory.status)
    }

    @Test
    fun runFullDiagnostics_reportsFail_whenThermalCritical() = runTest(testDispatcher) {
        // Critical 85°C
        fakeBackend.setFile("/sys/class/thermal/thermal_zone0/temp", "85000\n")

        val report = hardwareDiagnosticsEngine.runFullDiagnostics()
        assertEquals(DiagnosticStatus.FAIL, report.overallStatus)

        val thermal = report.subsystems.first { it.subsystem == HardwareSubsystem.THERMAL }
        assertEquals(DiagnosticStatus.FAIL, thermal.status)
        assertTrue(thermal.recommendation?.contains("Dangerous SoC temperature") == true)
    }

    @Test
    fun runFullDiagnostics_reportsWarning_whenRamAvailableLow() = runTest(testDispatcher) {
        // 5% RAM available (400 MB out of 8000 MB)
        fakeBackend.setFile(
            "/proc/meminfo",
            """
            MemTotal:        8000000 kB
            MemFree:          100000 kB
            MemAvailable:     400000 kB
            """.trimIndent()
        )

        val report = hardwareDiagnosticsEngine.runFullDiagnostics()
        val memory = report.subsystems.first { it.subsystem == HardwareSubsystem.MEMORY }
        assertEquals(DiagnosticStatus.WARNING, memory.status)
    }

    @Test
    fun runFullDiagnostics_reportsWarning_whenCoresAreOffline() = runTest(testDispatcher) {
        // Only cores 0-1 online
        fakeBackend.setFile("/sys/devices/system/cpu/online", "0-1\n")

        val report = hardwareDiagnosticsEngine.runFullDiagnostics()
        val cpu = report.subsystems.first { it.subsystem == HardwareSubsystem.CPU }
        assertEquals(DiagnosticStatus.WARNING, cpu.status)
        assertTrue(cpu.recommendation?.contains("Check governor settings") == true)
    }
}
