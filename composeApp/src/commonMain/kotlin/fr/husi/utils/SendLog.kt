package fr.husi.utils

import fr.husi.ktx.Logs
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.source
import java.io.IOException
import kotlinx.io.buffered
import kotlinx.io.readByteArray

internal expect fun dumpPlatformLogcat(): String

object SendLog {

    fun buildLog(externalAssetsDir: PlatformFile): String = buildString {
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
    }

    private fun getCoreLog(externalAssetsDir: PlatformFile): String {
        return try {
            val logFile = externalAssetsDir / "stderr.log"
            logFile.source().buffered().use { it.readByteArray() }.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            e.stackTraceToString()
        }
    }
}
