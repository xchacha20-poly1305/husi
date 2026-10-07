package fr.husi.repository

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.UserManager
import fr.husi.libcore.Service
import java.io.File
import org.koin.core.context.GlobalContext

interface AndroidRepository : Repository {
    val context: Context
    val configureIntent: (Context) -> PendingIntent
    val connectivity: ConnectivityManager
    val user: UserManager
    val power: PowerManager
    val wifi: WifiManager
    val packageManager: PackageManager

    val noBackupFilesDir: File

    /** The in-process core; only the `:bg` process has one. */
    val boxService: Service?

    suspend fun updateNotificationChannels()
}

fun resolveAndroidRepository(): AndroidRepository = GlobalContext.get().get()
