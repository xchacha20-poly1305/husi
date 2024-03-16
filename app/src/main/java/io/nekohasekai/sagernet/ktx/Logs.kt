package io.nekohasekai.sagernet.ktx

import libcore.Libcore
import java.io.InputStream
import java.io.OutputStream

object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    fun d(message: String) {
        Libcore.logDebug("[${mkTag()}] $message")
    }

    fun d(message: String, exception: Throwable) {
        Libcore.logDebug("[${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun i(message: String) {
        Libcore.logInfo("[${mkTag()}] $message")
    }

    fun i(message: String, exception: Throwable) {
        Libcore.logInfo("[${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun w(message: String) {
        Libcore.logWarning("[${mkTag()}] $message")
    }

    fun w(message: String, exception: Throwable) {
        Libcore.logWarning("[${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun w(exception: Throwable) {
        Libcore.logWarning("[${mkTag()}] " + exception.stackTraceToString())
    }

    fun e(message: String) {
        Libcore.logError("[${mkTag()}] $message")
    }

    fun e(message: String, exception: Throwable) {
        Libcore.logError("[${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun e(exception: Throwable) {
        Libcore.logError("[${mkTag()}] " + exception.stackTraceToString())
    }

}

fun InputStream.use(out: OutputStream) {
    use { input ->
        out.use { output ->
            input.copyTo(output)
        }
    }
}