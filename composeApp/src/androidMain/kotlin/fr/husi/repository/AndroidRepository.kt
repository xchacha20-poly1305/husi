package fr.husi.repository

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.UserManager
import java.io.File

interface AndroidRepository : Repository {
    val context: Context
    val configureIntent: (Context) -> PendingIntent
    val connectivity: ConnectivityManager
    val user: UserManager
    val power: PowerManager
    val wifi: WifiManager
    val packageManager: PackageManager

    val noBackupFilesDir: File
    fun getDatabasePath(name: String): File

    suspend fun updateNotificationChannels()
}

val androidRepo: AndroidRepository get() = repo as AndroidRepository
