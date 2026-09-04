package fr.husi.utils

import fr.husi.BuildConfig
import fr.husi.database.DataStore
import fr.husi.ktx.currentUtcReportTimestamp
import kotlinx.coroutines.runBlocking

object CrashReport {

    fun formatThrowable(throwable: Throwable): String {
        var format = throwable.javaClass.name
        val message = throwable.message
        if (!message.isNullOrBlank()) {
            format += ": $message"
        }
        format += "\n"

        format += throwable.stackTrace.joinToString("\n") {
            "    at ${it.className}.${it.methodName}(${it.fileName}:${if (it.isNativeMethod) "native" else it.lineNumber})"
        }

        val cause = throwable.cause
        if (cause != null) {
            format += "\n\nCaused by: " + formatThrowable(cause)
        }

        return format
    }

    fun buildEnvironmentReport(): String {
        var report = ""
        report += "husi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR.uppercase()}\n"
        report += "Date: ${getCurrentMilliSecondUTCTimeStamp()}\n\n"
        report += buildPlatformSystemInfoReport()
        return report
    }

    fun buildReportHeader(): String {
        var report = buildEnvironmentReport()

        try {
            report += "Settings: \n"
            runBlocking {
                report += DataStore.configurationStore.exportToString()
            }
        } catch (e: Exception) {
            report += "Export settings failed: " + formatThrowable(e)
        }

        report += "\n\n"

        return report
    }

    private fun getCurrentMilliSecondUTCTimeStamp(): String {
        return currentUtcReportTimestamp()
    }

}

internal expect fun buildPlatformSystemInfoReport(): String
