package fr.husi.ktx

import android.util.Log
import fr.husi.libcore.Libcore

actual object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    private fun logToAndroid(level: Int, tag: String, message: String) {
        when (level) {
            Log.DEBUG -> Log.d(tag, message)
            Log.INFO -> Log.i(tag, message)
            Log.WARN -> Log.w(tag, message)
            Log.ERROR -> Log.e(tag, message)
            else -> Log.println(level, tag, message)
        }
    }

    actual fun d(message: String) {
        val tag = mkTag()
        Libcore.logDebug("[$tag] $message")
        logToAndroid(Log.DEBUG, tag, message)
    }

    actual fun d(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logDebug("[$tag] $full")
        logToAndroid(Log.DEBUG, tag, full)
    }

    actual fun i(message: String) {
        val tag = mkTag()
        Libcore.logInfo("[$tag] $message")
        logToAndroid(Log.INFO, tag, message)
    }

    actual fun i(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logInfo("[$tag] $full")
        logToAndroid(Log.INFO, tag, full)
    }

    actual fun w(message: String) {
        val tag = mkTag()
        Libcore.logWarning("[$tag] $message")
        logToAndroid(Log.WARN, tag, message)
    }

    actual fun w(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logWarning("[$tag] $full")
        logToAndroid(Log.WARN, tag, full)
    }

    actual fun w(exception: Throwable) {
        val tag = mkTag()
        val full = exception.stackTraceToString()
        Libcore.logWarning("[$tag] $full")
        logToAndroid(Log.WARN, tag, full)
    }

    actual fun e(message: String) {
        val tag = mkTag()
        Libcore.logError("[$tag] $message")
        logToAndroid(Log.ERROR, tag, message)
    }

    actual fun e(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logError("[$tag] $full")
        logToAndroid(Log.ERROR, tag, full)
    }

    actual fun e(exception: Throwable) {
        val tag = mkTag()
        val full = exception.stackTraceToString()
        Libcore.logError("[$tag] $full")
        logToAndroid(Log.ERROR, tag, full)
    }

}
