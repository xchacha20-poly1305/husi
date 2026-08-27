package fr.husi.repository

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.UserManager
import io.github.vinceglb.filekit.PlatformFile
import org.koin.core.context.GlobalContext

interface AndroidRepository : Repository {
    val context: Context
    val configureIntent: (Context) -> PendingIntent
    val connectivity: ConnectivityManager
    val user: UserManager
    val power: PowerManager
    val wifi: WifiManager
    val packageManager: PackageManager

    val noBackupFilesDir: PlatformFile

    suspend fun updateNotificationChannels()
}

fun resolveAndroidRepository(): AndroidRepository = GlobalContext.get().get()
