package fr.husi.ktx

import fr.husi.libcore.Libcore

actual object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    actual fun d(message: String) {
        val tag = mkTag()
        Libcore.logDebug("[$tag] $message")
        System.err.println("D [$tag] $message")
    }

    actual fun d(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logDebug("[$tag] $full")
        System.err.println("D [$tag] $full")
    }

    actual fun i(message: String) {
        val tag = mkTag()
        Libcore.logInfo("[$tag] $message")
        System.err.println("I [$tag] $message")
    }

    actual fun i(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logInfo("[$tag] $full")
        System.err.println("I [$tag] $full")
    }

    actual fun w(message: String) {
        val tag = mkTag()
        Libcore.logWarning("[$tag] $message")
        System.err.println("W [$tag] $message")
    }

    actual fun w(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logWarning("[$tag] $full")
        System.err.println("W [$tag] $full")
    }

    actual fun w(exception: Throwable) {
        val tag = mkTag()
        val full = exception.stackTraceToString()
        Libcore.logWarning("[$tag] $full")
        System.err.println("W [$tag] $full")
    }

    actual fun e(message: String) {
        val tag = mkTag()
        Libcore.logError("[$tag] $message")
        System.err.println("E [$tag] $message")
    }

    actual fun e(message: String, exception: Throwable) {
        val tag = mkTag()
        val full = "$message\n${exception.stackTraceToString()}"
        Libcore.logError("[$tag] $full")
        System.err.println("E [$tag] $full")
    }

    actual fun e(exception: Throwable) {
        val tag = mkTag()
        val full = exception.stackTraceToString()
        Libcore.logError("[$tag] $full")
        System.err.println("E [$tag] $full")
    }

}
