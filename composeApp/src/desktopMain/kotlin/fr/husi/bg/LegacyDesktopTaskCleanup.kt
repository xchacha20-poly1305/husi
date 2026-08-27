package fr.husi.bg

import fr.husi.DesktopPaths
import fr.husi.ktx.Logs
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import java.io.File
import java.nio.charset.Charset

internal object LegacyDesktopTaskCleanup {
    private const val UNIT_PREFIX = "fr.husi.desktop"
    private const val WINDOWS_TASK_PREFIX = "Husi-"

    fun purge(taskIds: Iterable<String>) {
        for (taskId in taskIds) {
            runCatching {
                when (PlatformInfo.platform) {
                    Platform.Android -> error("Unsupported desktop platform")
                    Platform.Linux -> purgeLinuxTask(taskId)
                    Platform.MacOs -> purgeMacTask(taskId)
                    Platform.Windows -> purgeWindowsTask(taskId)
                }
            }.onFailure {
                Logs.w("purge legacy desktop task $taskId", it)
            }
        }
    }

    private fun purgeLinuxTask(taskId: String) {
        val serviceFile = linuxUnitFile(taskId, "service")
        val timerFile = linuxUnitFile(taskId, "timer")
        if (!timerFile.exists() && !serviceFile.exists()) return

        // `disable` is happy with a unit that exists but was never enabled, and only fails when
        // systemd cannot find the unit at all — which is exactly what the file tells us.
        if (timerFile.exists()) {
            runCatching {
                runCommand("systemctl", "--user", "disable", "--now", timerFile.name)
            }.onFailure {
                Logs.w("disable systemd timer ${timerFile.name}", it)
            }
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
            .resolve("$UNIT_PREFIX.$taskId.$suffix")
    }

    private fun purgeMacTask(taskId: String) {
        val label = "$UNIT_PREFIX.$taskId"
        val agentFile = DesktopPaths.macLaunchAgentsDir.resolve("$label.plist")
        // `bootout` addresses the agent by plist path, so without that file there is nothing
        // loaded to boot out and launchctl only answers "no such process".
        if (!agentFile.exists()) return

        runCatching {
            runCommand("launchctl", "bootout", macUserDomainTarget(), agentFile.absolutePath)
        }.onFailure {
            Logs.w("bootout launch agent $label", it)
        }
        deleteFileIfPresent(agentFile)
    }

    private fun macUserDomainTarget(): String {
        return "gui/${runCommand("id", "-u").trim()}"
    }

    private fun purgeWindowsTask(taskId: String) {
        val taskName = WINDOWS_TASK_PREFIX + taskId
        // Windows keeps the task registry to itself, so the query is the only way to ask.
        val exists = runCatching {
            runCommand("schtasks", "/query", "/tn", taskName)
        }.isSuccess
        if (!exists) return

        runCatching {
            runCommand("schtasks", "/delete", "/tn", taskName, "/f")
        }.onFailure {
            Logs.w("delete scheduled task $taskName", it)
        }
    }

    private fun runCommand(vararg args: String): String {
        val process = ProcessBuilder(args.toList())
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
}
