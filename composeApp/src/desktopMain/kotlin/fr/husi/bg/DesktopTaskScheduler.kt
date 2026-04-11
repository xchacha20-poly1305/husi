package fr.husi.bg

import fr.husi.buildLauncherCommand
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.platform.PlatformInfo
import fr.husi.quoteSystemdArgument
import fr.husi.quoteWindowsArgument
import fr.husi.xmlEscape
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object DesktopTaskScheduler {
    private lateinit var manager: DesktopTaskSchedulerManager

    fun initialize() {
        manager = DesktopTaskSchedulerManager()
    }

    suspend fun reconfigure(task: DesktopTaskDefinition) {
        if (!::manager.isInitialized) initialize()
        manager.reconfigure(task)
    }
}

internal data class MacLaunchAgentSchedule(
    val startIntervalSeconds: Long?,
    val runAtLoad: Boolean,
)

internal fun macLaunchAgentSchedule(schedule: DesktopTaskSchedule): MacLaunchAgentSchedule {
    val initialDelaySeconds = schedule.initialDelaySeconds.coerceAtLeast(0L)
    return if (initialDelaySeconds == 0L) {
        MacLaunchAgentSchedule(
            startIntervalSeconds = null,
            runAtLoad = true,
        )
    } else {
        MacLaunchAgentSchedule(
            startIntervalSeconds = initialDelaySeconds,
            runAtLoad = false,
        )
    }
}

private class DesktopTaskSchedulerManager {
    companion object {
        private const val LINUX_UNIT_PREFIX = "fr.husi.desktop"
        private const val WINDOWS_TASK_PREFIX = "Husi-"
        private val windowsDateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
        private val windowsTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    suspend fun reconfigure(task: DesktopTaskDefinition) {
        val schedule = task.schedule()
        if (schedule == null) {
            removeTask(task.id)
            return
        }

        val launcherCommand = buildLauncherCommand(*task.launcherArguments.toTypedArray())
        when {
            PlatformInfo.isLinux -> installLinuxTask(task.id, launcherCommand, schedule)
            PlatformInfo.isMacOs -> installMacTask(task.id, launcherCommand, schedule)
            PlatformInfo.isWindows -> installWindowsTask(task.id, launcherCommand, schedule)
            else -> error("Unsupported desktop platform")
        }
    }

    private fun removeTask(taskId: String) {
        when {
            PlatformInfo.isLinux -> removeLinuxTask(taskId)
            PlatformInfo.isMacOs -> removeMacTask(taskId)
            PlatformInfo.isWindows -> removeWindowsTask(taskId)
            else -> error("Unsupported desktop platform")
        }
    }

    private fun installLinuxTask(
        taskId: String,
        launcherCommand: List<String>,
        schedule: DesktopTaskSchedule,
    ) {
        val serviceFile = linuxUnitFile(taskId, "service")
        val timerFile = linuxUnitFile(taskId, "timer")
        serviceFile.parentFile.mkdirs()

        val serviceName = serviceFile.name
        val timerName = timerFile.name
        val execStart = launcherCommand.joinToString(" ", transform = ::quoteSystemdArgument)

        serviceFile.writeText(
            """
            [Unit]
            Description=Husi desktop task $taskId
            Wants=network-online.target
            After=network-online.target

            [Service]
            Type=oneshot
            ExecStart=$execStart
            """.trimIndent() + "\n",
        )
        timerFile.writeText(
            """
            [Unit]
            Description=Husi desktop task timer $taskId

            [Timer]
            Unit=$serviceName
            OnBootSec=${formatSystemdDuration(schedule.initialDelaySeconds)}
            OnUnitActiveSec=${formatSystemdDuration(schedule.repeatIntervalMinutes.toLong() * 60L)}

            [Install]
            WantedBy=timers.target
            """.trimIndent() + "\n",
        )

        runCommand("systemctl", "--user", "daemon-reload")
        runCommand("systemctl", "--user", "enable", "--now", timerName)
    }

    private fun removeLinuxTask(taskId: String) {
        val serviceFile = linuxUnitFile(taskId, "service")
        val timerFile = linuxUnitFile(taskId, "timer")
        runCatching {
            runCommand("systemctl", "--user", "disable", "--now", timerFile.name)
        }.onFailure {
            Logs.w("disable systemd timer ${timerFile.name}", it)
        }
        deleteFileIfPresent(timerFile)
        deleteFileIfPresent(serviceFile)
        runCatching {
            runCommand("systemctl", "--user", "daemon-reload")
        }.onFailure {
            Logs.w("reload systemd user units", it)
        }
    }

    private fun linuxUnitFile(taskId: String, suffix: String): File {
        val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
            ?.blankAsNull()
            ?.let(::File)
            ?: File(System.getProperty("user.home"), ".config")
        return xdgConfigHome.resolve("systemd").resolve("user")
            .resolve("$LINUX_UNIT_PREFIX.$taskId.$suffix")
    }

    private fun installMacTask(
        taskId: String,
        launcherCommand: List<String>,
        schedule: DesktopTaskSchedule,
    ) {
        val label = macLabel(taskId)
        val agentFile = macLaunchAgentFile(taskId)
        val agentSchedule = macLaunchAgentSchedule(schedule)
        agentFile.parentFile.mkdirs()

        runCatching {
            runLaunchCtl("bootout", macUserDomainTarget(), agentFile.absolutePath)
        }.onFailure {
            Logs.w("bootout launch agent $label", it)
        }

        val arguments = launcherCommand.joinToString(separator = "\n") {
            "    <string>${xmlEscape(it)}</string>"
        }
        val scheduleBlock = buildString {
            agentSchedule.startIntervalSeconds?.let {
                appendLine("    <key>StartInterval</key>")
                appendLine("    <integer>$it</integer>")
            }
            appendLine("    <key>RunAtLoad</key>")
            append("    <${if (agentSchedule.runAtLoad) "true" else "false"}/>")
        }
        agentFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>$label</string>
                <key>ProgramArguments</key>
                <array>
            $arguments
                </array>
            $scheduleBlock
            </dict>
            </plist>
            """.trimIndent() + "\n",
        )

        runCommand("launchctl", "bootstrap", macUserDomainTarget(), agentFile.absolutePath)
    }

    private fun removeMacTask(taskId: String) {
        val agentFile = macLaunchAgentFile(taskId)
        runCatching {
            runLaunchCtl("bootout", macUserDomainTarget(), agentFile.absolutePath)
        }.onFailure {
            Logs.w("bootout launch agent ${macLabel(taskId)}", it)
        }
        deleteFileIfPresent(agentFile)
    }

    private fun macLaunchAgentFile(taskId: String): File {
        return File(System.getProperty("user.home"), "Library")
            .resolve("LaunchAgents")
            .resolve("${macLabel(taskId)}.plist")
    }

    private fun macLabel(taskId: String): String = "$LINUX_UNIT_PREFIX.$taskId"

    private fun macUserDomainTarget(): String {
        return "gui/${runCommand("id", "-u").trim()}"
    }

    private fun installWindowsTask(
        taskId: String,
        launcherCommand: List<String>,
        schedule: DesktopTaskSchedule,
    ) {
        val commandLine = launcherCommand.joinToString(" ", transform = ::quoteWindowsArgument)
        val firstRunAt = LocalDateTime.now()
            .plusSeconds(schedule.initialDelaySeconds.coerceAtLeast(60L))
        runCommand(
            "schtasks",
            "/create",
            "/tn",
            windowsTaskName(taskId),
            "/tr",
            commandLine,
            "/sc",
            "once",
            "/sd",
            firstRunAt.format(windowsDateFormatter),
            "/st",
            firstRunAt.format(windowsTimeFormatter),
            "/ri",
            schedule.repeatIntervalMinutes.toString(),
            "/du",
            "9999:59",
            "/f",
        )
    }

    private fun removeWindowsTask(taskId: String) {
        runCatching {
            runCommand(
                "schtasks",
                "/delete",
                "/tn",
                windowsTaskName(taskId),
                "/f",
            )
        }.onFailure {
            Logs.w("delete scheduled task ${windowsTaskName(taskId)}", it)
        }
    }

    private fun windowsTaskName(taskId: String): String = WINDOWS_TASK_PREFIX + taskId

    private fun runLaunchCtl(vararg args: String): String {
        return runCommand(
            buildList {
                add("launchctl")
                addAll(args)
            },
        )
    }

    private fun runCommand(vararg args: String): String {
        return runCommand(args.toList())
    }

    private fun runCommand(args: List<String>): String {
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            output.ifBlank {
                "${args.joinToString(" ")} failed with exit code $exitCode"
            }
        }
        return output
    }

    private fun deleteFileIfPresent(file: File) {
        if (!file.exists()) return
        check(file.delete()) { "failed to delete ${file.absolutePath}" }
    }

    private fun formatSystemdDuration(seconds: Long): String {
        return "${seconds.coerceAtLeast(0L)}s"
    }
}
