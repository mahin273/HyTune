package com.mahin.hytune.ui.tools

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.sysfs.SysfsRepository
import com.mahin.hytune.domain.sysfs.engine.SysfsBrowserEngine
import com.mahin.hytune.domain.sysfs.model.SysfsNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SysfsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var repository: SysfsRepository
    private lateinit var viewModel: SysfsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBackend = FakePrivilegeBackend()
        val engine = SysfsBrowserEngine(fakeBackend, testDispatcher)
        repository = SysfsRepository(engine, testDispatcher)

        fakeBackend.registerCommandHandler("ls -la \"/sys\"") {
            CommandResult.success(
                listOf(
                    "drwxr-xr-x 2 root root 0 Jan 1 00:00 block",
                    "drwxr-xr-x 2 root root 0 Jan 1 00:00 devices",
                    "-rw-r--r-- 1 root root 0 Jan 1 00:00 power_state"
                )
            )
        }

        viewModel = SysfsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialNavigation_loadsSysNodes() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/sys", state.currentPath)
        assertEquals(3, state.nodes.size)
    }

    @Test
    fun selectFileNode_loadsContent() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        fakeBackend.registerCommandHandler("head -c 65536 \"/sys/power_state\"") {
            CommandResult.success(listOf("freeze mem"))
        }

        val fileNode = SysfsNode(
            path = "/sys/power_state",
            name = "power_state",
            isDirectory = false,
            isReadable = true,
            isWritable = true
        )

        viewModel.selectNode(fileNode)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.selectedNode)
        assertEquals("freeze mem", state.selectedNodeContent)
    }

    @Test
    fun writeNodeValue_updatesStatusMessage() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        fakeBackend.registerCommandHandler("echo -n 'mem' > \"/sys/power_state\"") {
            CommandResult.success()
        }
        fakeBackend.registerCommandHandler("head -c 65536 \"/sys/power_state\"") {
            CommandResult.success(listOf("mem"))
        }

        viewModel.writeNodeValue("/sys/power_state", "mem")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.statusMessage?.contains("Successfully written") == true)
        assertEquals("mem", state.selectedNodeContent)
    }
}
