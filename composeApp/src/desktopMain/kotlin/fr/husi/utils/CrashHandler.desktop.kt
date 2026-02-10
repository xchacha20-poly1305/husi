package fr.husi.utils

import fr.husi.ktx.Logs
import kotlin.system.exitProcess

actual object CrashHandler : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            Logs.e(thread.toString())
            Logs.e(throwable.stackTraceToString())
        }

        exitProcess(1)
    }
}
