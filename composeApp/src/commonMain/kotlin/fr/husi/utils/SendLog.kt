package fr.husi.utils

import fr.husi.ktx.Logs
import fr.husi.ktx.currentFileNameTimestamp
import java.io.File
import java.io.FileInputStream
import java.io.IOException

internal expect fun dumpPlatformLogcat(): String

data class LogExport(
    val fileName: String,
    val content: String,
)

data class RemoteLogTarget(
    val name: String,
    val url: String,
    val version: String,
    val logLevel: String,
)

object SendLog {

    private const val LOCAL_TARGET_NAME = "local"
    private const val UNNAMED_TARGET_NAME = "remote"
    private const val FILE_NAME_PREFIX = "husi"
    private const val FILE_NAME_EXTENSION = ".log"
    private const val CORE_LOG_FILE_NAME = "stderr.log"
    private const val REMOTE_BUFFER_NOTICE =
        "The lines below are what this client received after subscribing: they do not start at " +
                "the target's startup, and the target only streams what its own log level allows."
    private val unsafeFileNameCharacters = Regex("[^A-Za-z0-9._-]")

    fun buildLocalLog(externalAssetsDir: File): LogExport = LogExport(
        fileName = buildFileName(LOCAL_TARGET_NAME),
        content = buildString {
            append(CrashReport.buildReportHeader())
            appendLine("Logcat: ")
            appendLine()
            try {
                appendLine(dumpPlatformLogcat())
            } catch (e: IOException) {
                Logs.w(e)
                appendLine("Export logcat error: " + CrashReport.formatThrowable(e))
            }
            appendLine(getCoreLog(externalAssetsDir))
        },
    )

    fun buildRemoteLog(target: RemoteLogTarget, logLines: List<String>): LogExport {
        val targetName = target.name.ifBlank { UNNAMED_TARGET_NAME }
        return LogExport(
            fileName = buildFileName(targetName),
            content = buildString {
                appendLine("Client: ")
                appendLine()
                append(CrashReport.buildEnvironmentReport())
                appendLine()
                appendLine("Target: $targetName (${target.url})")
                appendLine("Version: ${target.version}")
                appendLine("Log level: ${target.logLevel}")
                appendLine()
                appendLine("Logs: ")
                appendLine(REMOTE_BUFFER_NOTICE)
                appendLine()
                for (line in logLines) {
                    appendLine(line)
                }
            },
        )
    }

    private fun buildFileName(targetName: String): String {
        val safeName = targetName.replace(unsafeFileNameCharacters, "-")
        return "$FILE_NAME_PREFIX-$safeName-${currentFileNameTimestamp()}$FILE_NAME_EXTENSION"
    }

    private fun getCoreLog(externalAssetsDir: File): String {
        return try {
            val logFile = externalAssetsDir.resolve(CORE_LOG_FILE_NAME)
            val stream = FileInputStream(logFile)
            stream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            e.stackTraceToString()
        }
    }
}
