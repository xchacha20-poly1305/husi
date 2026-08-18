package fr.husi

import dev.nucleusframework.core.runtime.SingleInstanceManager
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTaskForwardTest {

    private val lockFilesDir = createTempDirectory("husi-task-forward")

    private val configuration = SingleInstanceManager.Configuration(
        lockFilesDir = lockFilesDir,
        lockIdentifier = "fr.husi-test",
    )

    @AfterTest
    fun cleanUp() {
        lockFilesDir.toFile().deleteRecursively()
    }

    @Test
    fun `no lock file means no running instance`() {
        assertFalse(runningInstanceHoldsLock(configuration))
    }

    @Test
    fun `an unlocked lock file means the last instance is gone`() {
        Files.createFile(configuration.lockFilePath)

        assertFalse(runningInstanceHoldsLock(configuration))
    }

    @Test
    fun `a task request carries the task id to the running instance`() {
        assertTrue(sendTaskRequest(configuration, TASK_ID))

        assertEquals(
            listOf(RESTORE_PAYLOAD_TASK, TASK_ID),
            Files.readAllLines(configuration.restoreRequestFilePath),
        )
    }

    private companion object {
        const val TASK_ID = "subscription-auto-update"
    }
}
