package fr.husi.bg

import dev.nucleusframework.scheduler.DesktopTask
import dev.nucleusframework.scheduler.ExistingTaskPolicy
import dev.nucleusframework.scheduler.InternalSchedulerApi
import dev.nucleusframework.scheduler.TaskContext
import dev.nucleusframework.scheduler.TaskId
import dev.nucleusframework.scheduler.TaskRegistry
import dev.nucleusframework.scheduler.TaskResult
import dev.nucleusframework.scheduler.testing.TestDesktopTaskScheduler
import fr.husi.database.DataStore
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(InternalSchedulerApi::class)
class DesktopTaskSchedulerTest : HusiKoinTest() {

    private val taskId = TaskId(TASK_ID)
    private lateinit var scheduler: TestDesktopTaskScheduler

    @BeforeTest
    fun installTestScheduler() {
        scheduler = TestDesktopTaskScheduler().also { it.install() }
    }

    @AfterTest
    fun uninstallTestScheduler() {
        scheduler.uninstall()
    }

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @Test
    fun `an interval below the Nucleus floor is raised`() {
        val record = scheduledRecordOf(
            DesktopTaskSchedule(repeatIntervalMinutes = 1, initialDelaySeconds = 0L),
        )

        assertEquals(15, record.intervalMinutes)
    }

    @Test
    fun `a task that is already due runs immediately`() {
        val record = scheduledRecordOf(
            DesktopTaskSchedule(repeatIntervalMinutes = 60, initialDelaySeconds = 0L),
        )

        assertTrue(record.runImmediately)
    }

    @Test
    fun `a task that is not due yet waits for its interval`() {
        val record = scheduledRecordOf(
            DesktopTaskSchedule(repeatIntervalMinutes = 60, initialDelaySeconds = 600L),
        )

        assertFalse(record.runImmediately)
    }

    @Test
    fun `a record survives a round trip`() {
        val record = DesktopTaskScheduledRecord(intervalMinutes = 60, runImmediately = true)

        assertEquals(record, decodeScheduledRecord(record.encode()))
    }

    @Test
    fun `a damaged record decodes to nothing`() {
        assertNull(decodeScheduledRecord("60"))
        assertNull(decodeScheduledRecord("sixty/true"))
        assertNull(decodeScheduledRecord("60/yes"))
    }

    @Test
    fun `reconfigure registers a periodic task`() = runTest {
        DesktopTaskScheduler.reconfigure(taskDefinition(deferredSchedule()))

        val request = assertNotNull(scheduler.getEnqueuedRequest(taskId))
        assertEquals(60.minutes, request.interval)
        assertEquals(ExistingTaskPolicy.REPLACE, request.existingTaskPolicy)
    }

    @Test
    fun `reconfigure leaves the timer of an unchanged schedule alone`() = runTest {
        val task = taskDefinition(deferredSchedule())
        DesktopTaskScheduler.reconfigure(task)
        assertTrue(scheduler.advanceTimeBy(30.minutes, countingRegistry()).isEmpty())

        DesktopTaskScheduler.reconfigure(task)

        // Re-registering would have moved the first run to minute 90 instead of minute 60.
        assertEquals(1, scheduler.advanceTimeBy(40.minutes, countingRegistry()).size)
    }

    @Test
    fun `reconfigure re-registers a task whose interval changed`() = runTest {
        DesktopTaskScheduler.reconfigure(taskDefinition(deferredSchedule()))

        DesktopTaskScheduler.reconfigure(
            taskDefinition(
                DesktopTaskSchedule(repeatIntervalMinutes = 120, initialDelaySeconds = 600L),
            ),
        )

        assertEquals(120.minutes, assertNotNull(scheduler.getEnqueuedRequest(taskId)).interval)
    }

    @Test
    fun `reconfigure cancels a task that no longer has a schedule`() = runTest {
        DesktopTaskScheduler.reconfigure(taskDefinition(deferredSchedule()))

        DesktopTaskScheduler.reconfigure(taskDefinition(null))

        assertFalse(scheduler.isScheduled(taskId))
        // The forgotten record must not make the next reconfigure think the task is still there.
        DesktopTaskScheduler.reconfigure(taskDefinition(deferredSchedule()))
        assertTrue(scheduler.isScheduled(taskId))
    }

    private fun deferredSchedule() = DesktopTaskSchedule(
        repeatIntervalMinutes = 60,
        initialDelaySeconds = 600L,
    )

    private fun taskDefinition(schedule: DesktopTaskSchedule?) = object : DesktopTaskDefinition {
        override val id: String = TASK_ID
        override val launcherArguments: List<String> = listOf("--task", TASK_ID)
        override suspend fun schedule(): DesktopTaskSchedule? = schedule
        override suspend fun run() = Unit
    }

    private fun countingRegistry(): TaskRegistry = TaskRegistry.Builder()
        .register(taskId) {
            object : DesktopTask {
                override suspend fun doWork(context: TaskContext): TaskResult = TaskResult.Success
            }
        }
        .build()

    private companion object {
        private const val TASK_ID = "desktop-task-scheduler-test"
    }
}
