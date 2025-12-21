package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.ktx.Logs
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object SendLog {

    fun buildLog(externalAssetsDir: File): String = buildString {
        append(CrashHandler.buildReportHeader())
        appendLine("Logcat: ")
        appendLine()
        try {
            appendLine(
                Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-d"))
                    .inputStream.bufferedReader(Charsets.UTF_8)
                    .readText(),
            )
        } catch (e: IOException) {
            Logs.w(e)
            appendLine("Export logcat error: " + CrashHandler.formatThrowable(e))
        }
        appendLine(getCoreLog(externalAssetsDir))
    }

    private fun getCoreLog(externalAssetsDir: File): String {
        return try {
            val logFile = File(externalAssetsDir, "stderr.log")
            val stream = FileInputStream(logFile)
            stream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            e.stackTraceToString()
        }
    }
}
