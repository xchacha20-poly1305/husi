package fr.husi.repository

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Above every platform's PID range, so no running process can claim it. */
private const val UNREACHABLE_PID = "4000000000"

class DesktopRepositoryTest {

    private lateinit var dataDir: File

    @BeforeTest
    fun setUpDataDir() {
        dataDir = createTempDirectory("husi-desktop-repository").toFile()
    }

    @AfterTest
    fun tearDownDataDir() {
        dataDir.deleteRecursively()
    }

    @Test
    fun `the primary instance owns the well-known core dir`() {
        val repository = FakeDesktopRepository(dataDir)

        assertEquals(repository.coreDir, repository.coreRunDir)
    }

    @Test
    fun `a secondary instance gets a core dir of its own`() {
        val primary = FakeDesktopRepository(dataDir)
        val secondary = FakeDesktopRepository(dataDir, instanceId = UNREACHABLE_PID)

        assertNotEquals(primary.coreRunDir, secondary.coreRunDir)
        assertEquals(primary.coreDir, secondary.coreRunDir.parentFile.parentFile)
        assertTrue(secondary.coreRunDir.isDirectory)
    }

    @Test
    fun `releaseCoreRunDir drops a secondary dir and keeps the primary one`() {
        val secondary = FakeDesktopRepository(dataDir, instanceId = UNREACHABLE_PID)
        secondary.releaseCoreRunDir()
        assertFalse(secondary.coreRunDir.exists())

        val primary = FakeDesktopRepository(dataDir)
        primary.releaseCoreRunDir()
        assertTrue(primary.coreRunDir.isDirectory)
    }

    @Test
    fun `pruneStaleCoreRunDirs drops only the dirs of instances that are gone`() {
        val dead = FakeDesktopRepository(dataDir, instanceId = UNREACHABLE_PID)
        val live = FakeDesktopRepository(
            dataDir,
            instanceId = DesktopRepository.currentInstanceId(),
        )
        val foreign = dead.coreRunDir.parentFile.resolve("not-a-pid").apply { mkdirs() }

        FakeDesktopRepository(dataDir).pruneStaleCoreRunDirs()

        assertFalse(dead.coreRunDir.exists())
        assertTrue(live.coreRunDir.isDirectory)
        assertTrue(foreign.isDirectory)
    }
}
