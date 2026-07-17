package fr.husi.bg

import fr.husi.ktx.Logs
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.app_name
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

object DesktopNotificationCenter {

    suspend fun show(title: String, message: String) {
        runCatching {
            when {
                PlatformInfo.isLinux -> showLinux(title, message)
                PlatformInfo.isMacOs -> showMacOs(title, message)
                PlatformInfo.isWindows -> showWindows(title, message)
            }
        }.onFailure {
            Logs.w("show desktop notification", it)
        }
    }

    private suspend fun showLinux(title: String, message: String) {
        runCommand(
            listOf(
                "gdbus",
                "call",
                "--session",
                "--dest",
                "org.freedesktop.Notifications",
                "--object-path",
                "/org/freedesktop/Notifications",
                "--method",
                "org.freedesktop.Notifications.Notify",
                resolveRepository().getString(Res.string.app_name),
                "0",
                "",
                title,
                message,
                "[]",
                "{}",
                "-1",
            ),
        )
    }

    private suspend fun showMacOs(title: String, message: String) {
        runCommand(
            listOf(
                "osascript", "-e",
                "display notification ${appleScriptString(message)} with title ${
                    appleScriptString(
                        title,
                    )
                }",
            ),
        )
    }

    private suspend fun showWindows(title: String, message: String) {
        val script = $$"""
            Add-Type -AssemblyName System.Windows.Forms
            `$notification = New-Object System.Windows.Forms.NotifyIcon
            `$notification.Icon = [System.Drawing.SystemIcons]::Information
            `$notification.Visible = `$true
            `$notification.ShowBalloonTip(10000, $${powerShellString(title)}, $${
            powerShellString(
                message,
            )
        }, [System.Windows.Forms.ToolTipIcon]::Information)
            Start-Sleep -Seconds 10
            `$notification.Dispose()
        """.trimIndent()
        val encodedScript =
            Base64.getEncoder().encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))
        runCommand(
            listOf(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-EncodedCommand",
                encodedScript,
            ),
            timeoutSeconds = 15,
        )
    }

    private suspend fun runCommand(command: List<String>, timeoutSeconds: Long = 5) {
        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("notification command timed out: ${command.first()}")
        }
        check(process.exitValue() == 0) {
            "notification command failed: ${command.first()} (exit ${process.exitValue()})"
        }
    }

    private fun appleScriptString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").let { "\"$it\"" }

    private fun powerShellString(value: String): String =
        "'${value.replace("'", "''")}'"
}
