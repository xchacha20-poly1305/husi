package fr.husi.bg

import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.nucleusframework.scheduler.ExistingTaskPolicy
import dev.nucleusframework.scheduler.SchedulerConfig
import dev.nucleusframework.scheduler.TaskId
import dev.nucleusframework.scheduler.TaskRequest
import fr.husi.buildLauncherCommand
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import kotlin.time.Duration.Companion.minutes
import dev.nucleusframework.scheduler.DesktopTaskScheduler as NucleusScheduler

internal object DesktopTaskScheduler {

    fun initialize() {
        // Nucleus appends its own trigger flag to this command line.
        val launcherCommand = buildLauncherCommand()
        SchedulerConfig.executablePath = launcherCommand.first()
        SchedulerConfig.executableArguments = launcherCommand.drop(1)
        LegacyDesktopTaskCleanup.purge(DesktopTaskRegistry.ids)

        if (!NucleusScheduler.isAvailable()) {
            Logs.w("no OS task scheduler available: background updates will not run")
        }
    }

    suspend fun reconfigure(task: DesktopTaskDefinition) {
        val taskId = TaskId(task.id)
        val schedule = task.schedule()
        if (schedule == null) {
            NucleusScheduler.cancel(taskId)
            writeScheduledRecord(task.id, null)
            return
        }

        val record = scheduledRecordOf(schedule)
        if (record == readScheduledRecord(task.id) && NucleusScheduler.isScheduled(taskId)) {
            // Re-registering an unchanged schedule restarts its timer from zero, so an app
            // restarted more often than the interval would never reach its first run.
            return
        }

        if (NucleusScheduler.enqueue(taskRequestOf(taskId, record))) {
            writeScheduledRecord(task.id, record)
        } else {
            Logs.w("schedule desktop task ${task.id}: Nucleus refused the request")
            writeScheduledRecord(task.id, null)
        }
    }
}

internal data class DesktopTaskScheduledRecord(
    val intervalMinutes: Int,
    val runImmediately: Boolean,
)

/** Nucleus rejects anything shorter, just like WorkManager does on Android. */
private const val MIN_INTERVAL_MINUTES = 15

internal fun scheduledRecordOf(schedule: DesktopTaskSchedule): DesktopTaskScheduledRecord {
    return DesktopTaskScheduledRecord(
        intervalMinutes = schedule.repeatIntervalMinutes.fastCoerceAtLeast(MIN_INTERVAL_MINUTES),
        // Nucleus knows no arbitrary initial delay: a task that is already due runs at once,
        // one that is not waits a full interval, and the runner skips whatever is not due yet.
        runImmediately = schedule.initialDelaySeconds <= 0L,
    )
}

internal fun taskRequestOf(taskId: TaskId, record: DesktopTaskScheduledRecord): TaskRequest {
    return TaskRequest.periodic(taskId, record.intervalMinutes.minutes) {
        runImmediately(record.runImmediately)
        // Reaching this point means the schedule changed, so the old timer has to go.
        existingTaskPolicy(ExistingTaskPolicy.REPLACE)
    }
}

private const val RECORD_KEY_PREFIX = "desktopTaskSchedule."
private const val RECORD_SEPARATOR = "/"

private fun recordKey(taskId: String): Preferences.Key<String> {
    return stringPreferencesKey(RECORD_KEY_PREFIX + taskId)
}

internal fun DesktopTaskScheduledRecord.encode(): String {
    return "$intervalMinutes$RECORD_SEPARATOR$runImmediately"
}

internal fun decodeScheduledRecord(value: String): DesktopTaskScheduledRecord? {
    val fields = value.split(RECORD_SEPARATOR)
    if (fields.size != 2) return null
    return DesktopTaskScheduledRecord(
        intervalMinutes = fields[0].toIntOrNull() ?: return null,
        runImmediately = fields[1].toBooleanStrictOrNull() ?: return null,
    )
}

private suspend fun readScheduledRecord(taskId: String): DesktopTaskScheduledRecord? {
    return DataStore.configurationStore.readValue(recordKey(taskId))
        ?.let(::decodeScheduledRecord)
}

private suspend fun writeScheduledRecord(taskId: String, record: DesktopTaskScheduledRecord?) {
    val key = recordKey(taskId)
    DataStore.configurationStore.edit { preferences ->
        if (record == null) {
            preferences.remove(key)
        } else {
            preferences[key] = record.encode()
        }
    }
}
