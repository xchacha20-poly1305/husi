package fr.husi.ktx

import android.util.Log
import fr.husi.libcore.Libcore
import fr.husi.proto.daemon.LogLevel
import java.io.File

actual object Logs {

    @Volatile
    private var file: LogFile? = null

    @Volatile
    private var coreAttached = false

    fun openFile(logFile: File, level: Int) {
        file?.close()
        file = LogFile(logFile, LogLevel.forNumber(level) ?: LogLevel.WARN, truncate = true)
    }

    fun attachCore() {
        coreAttached = true
    }

    actual fun clearFile() {
        file?.clear()
    }

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            else -> Log.e(tag, message)
        }
        if (coreAttached) {
            logToCore(level, "[$tag] $message")
        } else {
            file?.write(level, tag, message)
        }
    }

    private fun logToCore(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> Libcore.logDebug(message)
            LogLevel.INFO -> Libcore.logInfo(message)
            LogLevel.WARN -> Libcore.logWarning(message)
            else -> Libcore.logError(message)
        }
    }

    private fun withStackTrace(message: String, exception: Throwable) =
        "$message\n${exception.stackTraceToString()}"

    actual fun d(message: String) {
        log(LogLevel.DEBUG, mkTag(), message)
    }

    actual fun d(message: String, exception: Throwable) {
        log(LogLevel.DEBUG, mkTag(), withStackTrace(message, exception))
    }

    actual fun i(message: String) {
        log(LogLevel.INFO, mkTag(), message)
    }

    actual fun i(message: String, exception: Throwable) {
        log(LogLevel.INFO, mkTag(), withStackTrace(message, exception))
    }

    actual fun w(message: String) {
        log(LogLevel.WARN, mkTag(), message)
    }

    actual fun w(message: String, exception: Throwable) {
        log(LogLevel.WARN, mkTag(), withStackTrace(message, exception))
    }

    actual fun w(exception: Throwable) {
        log(LogLevel.WARN, mkTag(), exception.stackTraceToString())
    }

    actual fun e(message: String) {
        log(LogLevel.ERROR, mkTag(), message)
    }

    actual fun e(message: String, exception: Throwable) {
        log(LogLevel.ERROR, mkTag(), withStackTrace(message, exception))
    }

    actual fun e(exception: Throwable) {
        log(LogLevel.ERROR, mkTag(), exception.stackTraceToString())
    }

}
