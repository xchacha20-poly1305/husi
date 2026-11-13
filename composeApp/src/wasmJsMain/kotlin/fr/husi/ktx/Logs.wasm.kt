@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.ktx

actual object Logs {

    actual fun clearFile() = Unit

    private fun withStackTrace(message: String, exception: Throwable) =
        "$message\n${exception.stackTraceToString()}"

    actual fun d(message: String) {
        writeConsole("debug", message)
    }

    actual fun d(message: String, exception: Throwable) {
        writeConsole("debug", withStackTrace(message, exception))
    }

    actual fun i(message: String) {
        writeConsole("info", message)
    }

    actual fun i(message: String, exception: Throwable) {
        writeConsole("info", withStackTrace(message, exception))
    }

    actual fun w(message: String) {
        writeConsole("warn", message)
    }

    actual fun w(message: String, exception: Throwable) {
        writeConsole("warn", withStackTrace(message, exception))
    }

    actual fun w(exception: Throwable) {
        writeConsole("warn", exception.stackTraceToString())
    }

    actual fun e(message: String) {
        writeConsole("error", message)
    }

    actual fun e(message: String, exception: Throwable) {
        writeConsole("error", withStackTrace(message, exception))
    }

    actual fun e(exception: Throwable) {
        writeConsole("error", exception.stackTraceToString())
    }

}

@JsFun("(level, message) => console[level](message)")
private external fun writeConsole(level: String, message: String)
