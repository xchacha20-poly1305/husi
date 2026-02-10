package fr.husi.ktx

import android.content.Intent
import com.jakewharton.processphoenix.ProcessPhoenix
import fr.husi.repository.androidRepo

actual fun restartApplication() {
    ProcessPhoenix.triggerRebirth(
        androidRepo.context,
        Intent(androidRepo.context, Class.forName("fr.husi.ui.MainActivity")),
    )
}

actual fun exitApplication() {
    android.os.Process.killProcess(android.os.Process.myPid())
}
