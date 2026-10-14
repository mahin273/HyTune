package com.mahin.hytune.core.privilege

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UnprivilegedBackendTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var backend: UnprivilegedBackend

    @Before
    fun setUp() {
        backend = UnprivilegedBackend()
    }

    @Test
    fun tierAndCapabilities_areNoneAndStrictlyLimited() {
        assertEquals(PrivilegeTier.NONE, backend.tier)
        assertTrue(backend.hasCapability(Capability.SYSFS_READ))

        assertFalse(backend.hasCapability(Capability.SYSFS_WRITE))
        assertFalse(backend.hasCapability(Capability.APP_FREEZE))
        assertFalse(backend.hasCapability(Capability.DUMPSYS))
        assertFalse(backend.hasCapability(Capability.PROCESS_CONTROL))
        assertFalse(backend.hasCapability(Capability.SETTINGS_WRITE))
    }

    @Test
    fun writeFile_alwaysReturnsSecurityException() = runTest {
        val result = backend.writeFile("/sys/devices/system/cpu/cpu0/online", "0")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun readFile_validExistingFile_returnsContent() = runTest {
        val testFile = tempFolder.newFile("sample_proc.txt")
        testFile.writeText("processor: 0\nmodel name: ARMv8")

        val result = backend.readFile(testFile.absolutePath)

        assertTrue(result.isSuccess)
        assertEquals("processor: 0\nmodel name: ARMv8", result.getOrNull())
    }

    @Test
    fun readFile_missingFile_returnsNoSuchFileException() = runTest {
        val missingFile = File(tempFolder.root, "non_existent.txt")

        val result = backend.readFile(missingFile.absolutePath)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchFileException)
    }

    @Test
    fun exists_and_listFiles_workOnLocalFilesystem() = runTest {
        val subDir = tempFolder.newFolder("sub_dir")
        File(subDir, "file_a.txt").writeText("A")
        File(subDir, "file_b.txt").writeText("B")

        assertTrue(backend.exists(subDir.absolutePath))
        assertFalse(backend.exists(File(tempFolder.root, "ghost_dir").absolutePath))

        val listResult = backend.listFiles(subDir.absolutePath)
        assertTrue(listResult.isSuccess)
        assertEquals(listOf("file_a.txt", "file_b.txt"), listResult.getOrNull())
    }
}
