package fr.husi.repository

import fr.husi.ktx.Logs
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Programs that ask the platform for administrator privileges. Only [PKEXEC] is
 * genuinely optional: polkit is absent from headless and minimal installs, while
 * osascript and powershell ship with their systems.
 */
private const val PKEXEC = "pkexec"
private const val OSASCRIPT = "osascript"
private const val POWERSHELL = "powershell"

/** The prompt waits on a human, so it is generous rather than snappy. */
private const val ELEVATION_TIMEOUT_MINUTES = 10L

/** How much of the program's output is kept for the failure message. */
private const val MAX_REPORTED_OUTPUT_LENGTH = 500

/** Outcome of an elevated `husi-core service install`. */
sealed interface DaemonInstallResult {
    data object Success : DaemonInstallResult
    data object Cancelled : DaemonInstallResult

    /** [program] could not be started, so no privilege prompt was ever shown. */
    data class ElevationUnavailable(val program: String) : DaemonInstallResult
    data class Failed(val message: String) : DaemonInstallResult
}

/**
 * Runs `husi-core service install` with a single elevation prompt
 * (pkexec / osascript / UAC). Shared by Settings install and update actions.
 */
suspend fun installDaemon(): DaemonInstallResult = runInterruptible(Dispatchers.IO) {
    val binary = resolveHusiCoreBinary()
        ?: return@runInterruptible DaemonInstallResult.Failed("husi-core binary not found")

    when (PlatformInfo.platform) {
        Platform.Linux -> installDaemonLinux(binary)
        Platform.MacOs -> installDaemonMacOs(binary)
        Platform.Windows -> installDaemonWindows(binary)
        Platform.Android -> DaemonInstallResult.Failed("daemon install is not supported on Android")
    }
}

private fun installDaemonLinux(binary: File): DaemonInstallResult {
    val command = listOf(PKEXEC, binary.absolutePath, "service", "install")
    Logs.i("daemon install: ${command.joinToString(" ")}")
    val outcome = when (val elevation = runElevated(command)) {
        is ElevationOutcome.Unavailable -> return elevationUnavailable(elevation)
        is ElevationOutcome.Completed -> elevation
    }
    return when (outcome.exitCode) {
        0 -> {
            Logs.i("daemon install: success")
            DaemonInstallResult.Success
        }
        // pkexec: auth failure / dialog dismissed commonly yields 126 or 127.
        126, 127 -> {
            Logs.i("daemon install: cancelled (exit ${outcome.exitCode})")
            DaemonInstallResult.Cancelled
        }
        else -> {
            val message = outcome.stderrTail.ifBlank { "exit ${outcome.exitCode}" }
            Logs.w("daemon install failed: $message")
            DaemonInstallResult.Failed(message)
        }
    }
}

private fun installDaemonMacOs(binary: File): DaemonInstallResult {
    val quotedBinary = posixSingleQuote(binary.absolutePath)
    val shell = "$quotedBinary service install"
    // AppleScript string literals only accept double quotes.
    val command = listOf(
        OSASCRIPT,
        "-e",
        "do shell script ${appleScriptQuote(shell)} with administrator privileges",
    )
    Logs.i("daemon install: osascript elevated service install (${binary.absolutePath})")
    val outcome = when (val elevation = runElevated(command)) {
        is ElevationOutcome.Unavailable -> return elevationUnavailable(elevation)
        is ElevationOutcome.Completed -> elevation
    }
    return when {
        outcome.exitCode == 0 -> {
            Logs.i("daemon install: success")
            DaemonInstallResult.Success
        }
        isMacOsUserCancelled(outcome) -> {
            Logs.i("daemon install: cancelled")
            DaemonInstallResult.Cancelled
        }
        else -> {
            val message = outcome.stderrTail.ifBlank { "exit ${outcome.exitCode}" }
            Logs.w("daemon install failed: $message")
            DaemonInstallResult.Failed(message)
        }
    }
}

private fun installDaemonWindows(binary: File): DaemonInstallResult {
    val path = binary.absolutePath.replace("'", "''")
    val ps = buildString {
        append($$"$p = Start-Process -FilePath '")
        append(path)
        append("' -ArgumentList 'service','install' -Verb RunAs -Wait -PassThru; ")
        append($$"if ($null -eq $p) { exit 1223 }; ")
        append($$"exit $p.ExitCode")
    }
    val command = listOf(POWERSHELL, "-NoProfile", "-Command", ps)
    Logs.i("daemon install: powershell Start-Process -Verb RunAs (${binary.absolutePath})")
    val outcome = when (val elevation = runElevated(command)) {
        is ElevationOutcome.Unavailable -> return elevationUnavailable(elevation)
        is ElevationOutcome.Completed -> elevation
    }
    return when {
        outcome.exitCode == 0 -> {
            Logs.i("daemon install: success")
            DaemonInstallResult.Success
        }
        isWindowsUserCancelled(outcome) -> {
            Logs.i("daemon install: cancelled")
            DaemonInstallResult.Cancelled
        }
        else -> {
            val message = outcome.stderrTail.ifBlank { "exit ${outcome.exitCode}" }
            Logs.w("daemon install failed: $message")
            DaemonInstallResult.Failed(message)
        }
    }
}

/** What happened when the platform's elevation program was spawned. */
private sealed interface ElevationOutcome {
    /** The program could not be started at all — most often it is not installed. */
    data class Unavailable(val program: String) : ElevationOutcome

    data class Completed(
        val exitCode: Int,
        val stderrTail: String,
    ) : ElevationOutcome
}

private fun elevationUnavailable(
    outcome: ElevationOutcome.Unavailable,
): DaemonInstallResult.ElevationUnavailable {
    Logs.w("daemon install: cannot run ${outcome.program}")
    return DaemonInstallResult.ElevationUnavailable(outcome.program)
}

private fun runElevated(command: List<String>): ElevationOutcome {
    val program = command.first()
    val process = try {
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
    } catch (e: IOException) {
        Logs.w("daemon install: spawning $program failed", e)
        return ElevationOutcome.Unavailable(program)
    }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val finished = process.waitFor(ELEVATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
    val exitCode = if (finished) {
        process.exitValue()
    } else {
        process.destroyForcibly()
        -1
    }
    val tail = output.trim().takeLast(MAX_REPORTED_OUTPUT_LENGTH)
    return ElevationOutcome.Completed(exitCode = exitCode, stderrTail = tail)
}

/** Double-quote a string as an AppleScript string literal. */
private fun appleScriptQuote(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

/** POSIX single-quote a string for embedding in a shell script. */
private fun posixSingleQuote(value: String): String {
    return "'" + value.replace("'", "'\\''") + "'"
}

private fun isMacOsUserCancelled(outcome: ElevationOutcome.Completed): Boolean {
    if (outcome.exitCode == -128) return true
    val text = outcome.stderrTail.lowercase()
    return text.contains("user canceled") ||
        text.contains("user cancelled") ||
        text.contains("(-128)")
}

private fun isWindowsUserCancelled(outcome: ElevationOutcome.Completed): Boolean {
    // 1223 = ERROR_CANCELLED; PowerShell often surfaces cancel text when UAC is declined.
    if (outcome.exitCode == 1223) return true
    val text = outcome.stderrTail.lowercase()
    return text.contains("canceled by the user") ||
        text.contains("cancelled by the user") ||
        text.contains("0x4c7") ||
        text.contains("operation was canceled") ||
        text.contains("operation was cancelled")
}
