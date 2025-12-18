package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.use
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object SendLog {

    fun buildLog(cacheDir: File, externalAssetsDir: File, title: String): File {
        // https://developer.android.com/reference/java/io/File.html#createTempFile(java.lang.String,%20java.lang.String,%20java.io.File)
        // Java stupid limit: prefix must at least three characters long
        val logFile = File.createTempFile(
            title,
            ".log",
            File(cacheDir, "log").also { it.mkdirs() },
        )

        val content = buildString {
            appendLine(CrashHandler.buildReportHeader())
            appendLine("Logcat: \n")
            appendLine(getCoreLog(externalAssetsDir, 0))
        }

        try {
            Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.use(
                FileOutputStream(logFile, true),
            )
            logFile.writeText(content)
            logFile.appendText("\n")
        } catch (e: IOException) {
            Logs.w(e)
            logFile.appendText("Export logcat error: " + CrashHandler.formatThrowable(e))
        }
        return logFile
    }

    fun buildLog(externalAssetsDir: File): String = buildString {
        append(CrashHandler.buildReportHeader())
        appendLine("Logcat: ")
        appendLine()
        appendLine(getCoreLog(externalAssetsDir, 0))
    }

    private fun getCoreLog(externalAssetsDir: File, max: Long): ByteArray {
        val logFile = File(externalAssetsDir, "stderr.log")
        return try {
            val len = logFile.length()
            val stream = FileInputStream(logFile)
            if (max in 1 until len) {
                stream.skip(len - max)
            }
            stream.use { it.readBytes() }
        } catch (e: Exception) {
            e.stackTraceToString().toByteArray()
        }
    }
}
