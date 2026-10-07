package fr.husi.ktx

import fr.husi.proto.daemon.LogLevel
import java.io.File

/**
 * Prints every message to stderr. Once [openFile] has run, messages at or above the configured
 * level also go to the log file that bug reports attach, together with what the core host
 * process prints, which [fr.husi.repository.CoreHostController] forwards here.
 */
actual object Logs {

    @Volatile
    private var file: LogFile? = null

    /** Starts [logFile] afresh and writes messages at or above [level] to it from now on. */
    fun openFile(logFile: File, level: Int) {
        file?.close()
        file = LogFile(logFile, LogLevel.forNumber(level) ?: LogLevel.WARN, truncate = true)
    }

    actual fun clearFile() {
        file?.clear()
    }

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        System.err.print("${level.name} [$tag] $message\n")
        file?.write(level, tag, message)
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
