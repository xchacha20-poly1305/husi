package fr.husi.bg

import fr.husi.DesktopPaths
import fr.husi.buildLauncherCommand
import fr.husi.ktx.Logs
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import fr.husi.quoteSystemdArgument
import fr.husi.quoteWindowsArgument
import fr.husi.xmlEscape
import java.io.File
import java.nio.charset.Charset
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
    }

    suspend fun reconfigure(task: DesktopTaskDefinition) {
        val schedule = task.schedule()
        if (schedule == null) {
            removeTask(task.id)
            return
        }

        val launcherCommand = buildLauncherCommand(*task.launcherArguments.toTypedArray())
        when (PlatformInfo.platform) {
            Platform.Android -> error("Unsupported desktop platform")
            Platform.Linux -> installLinuxTask(task.id, launcherCommand, schedule)
            Platform.MacOs -> installMacTask(task.id, launcherCommand, schedule)
            Platform.Windows -> installWindowsTask(task.id, launcherCommand, schedule)
        }
    }

    private fun removeTask(taskId: String) {
        when (PlatformInfo.platform) {
            Platform.Android -> error("Unsupported desktop platform")
            Platform.Linux -> removeLinuxTask(taskId)
            Platform.MacOs -> removeMacTask(taskId)
            Platform.Windows -> removeWindowsTask(taskId)
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
        return DesktopPaths.linuxSystemdUserDir
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
        return DesktopPaths.macLaunchAgentsDir
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
        val firstRunAt = LocalDateTime.now()
            .plusSeconds(schedule.initialDelaySeconds.coerceAtLeast(60L))
        // schtasks /sd /st parse dates per the user's locale (e.g. yyyy/M/d on zh-CN), so a fixed
        // pattern always fails on non-en-US systems. Import an XML definition instead — its
        // <StartBoundary> is ISO 8601 and locale-independent.
        val xml = buildWindowsTaskXml(launcherCommand, firstRunAt, schedule.repeatIntervalMinutes)
        val xmlFile = File.createTempFile("husi-task-", ".xml")
        try {
            xmlFile.writeText(xml, Charsets.UTF_16)
            runCommand(
                "schtasks",
                "/create",
                "/tn",
                windowsTaskName(taskId),
                "/xml",
                xmlFile.absolutePath,
                "/f",
            )
        } finally {
            xmlFile.delete()
        }
    }

    private fun buildWindowsTaskXml(
        launcherCommand: List<String>,
        firstRunAt: LocalDateTime,
        repeatIntervalMinutes: Int,
    ): String {
        val command = launcherCommand.first()
        val arguments = launcherCommand
            .drop(1)
            .joinToString(" ", transform = ::quoteWindowsArgument)
        val startBoundary = firstRunAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return """
            <?xml version="1.0" encoding="UTF-16"?>
            <Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
              <Triggers>
                <TimeTrigger>
                  <StartBoundary>$startBoundary</StartBoundary>
                  <Repetition>
                    <Interval>PT${repeatIntervalMinutes}M</Interval>
                    <StopAtDurationEnd>false</StopAtDurationEnd>
                  </Repetition>
                  <Enabled>true</Enabled>
                </TimeTrigger>
              </Triggers>
              <Principals>
                <Principal id="Author">
                  <LogonType>InteractiveToken</LogonType>
                  <RunLevel>LeastPrivilege</RunLevel>
                </Principal>
              </Principals>
              <Settings>
                <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
                <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
                <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
                <StartWhenAvailable>true</StartWhenAvailable>
                <AllowStartOnDemand>true</AllowStartOnDemand>
                <Enabled>true</Enabled>
                <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
                <Priority>7</Priority>
              </Settings>
              <Actions Context="Author">
                <Exec>
                  <Command>${xmlEscape(command)}</Command>
                  <Arguments>${xmlEscape(arguments)}</Arguments>
                </Exec>
              </Actions>
            </Task>
            """.trimIndent() + "\n"
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
        val output = process.inputStream.bufferedReader(nativeCharset).use { it.readText().trim() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            output.ifBlank {
                "${args.joinToString(" ")} failed with exit code $exitCode"
            }
        }
        return output
    }

    /** Subprocess stdout uses the OS native code page (e.g. GBK on zh-CN Windows). Since JEP 400
     * (JDK 18+) Charset.defaultCharset() is UTF-8 on Windows, which mangles non-ASCII output;
     * native.encoding is the JDK-standard way to recover the OS encoding.
     */
    private val nativeCharset: Charset = run {
        val name = System.getProperty("native.encoding")
            ?: System.getProperty("sun.jnu.encoding")
        name?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: Charset.defaultCharset()
    }

    private fun deleteFileIfPresent(file: File) {
        if (!file.exists()) return
        check(file.delete()) { "failed to delete ${file.absolutePath}" }
    }

    private fun formatSystemdDuration(seconds: Long): String {
        return "${seconds.coerceAtLeast(0L)}s"
    }
}
