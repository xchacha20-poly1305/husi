package fr.husi.utils

import android.content.Intent
import android.util.Log
import com.jakewharton.processphoenix.ProcessPhoenix
import fr.husi.ktx.Logs
import fr.husi.repository.androidRepo

actual object CrashHandler : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // note: libc / go panic is in android log

        runCatching {
            Log.e(thread.toString(), throwable.stackTraceToString())
        }

        runCatching {
            Logs.e(thread.toString())
            Logs.e(throwable.stackTraceToString())
        }

        ProcessPhoenix.triggerRebirth(
            androidRepo.context,
            Intent(androidRepo.context, Class.forName("fr.husi.ui.BlankActivity"))
                .putExtra("log_title", "husi_crash"),
        )
    }
}
