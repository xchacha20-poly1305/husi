package io.nekohasekai.sagernet.repository

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.UserManager
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import java.io.File

lateinit var repo: Repository

interface Repository {
    val context: Context
    val isMainProcess: Boolean
    val isBgProcess: Boolean

    /* https://developer.android.com/reference/android/content/Context#getSystemService(java.lang.String)
    * Note: System services obtained via this API may be closely associated
    * with the Context in which they are obtained from. In general, do not share
    * the service objects between various different contexts
    *  (Activities, Applications, Services, Providers, etc.)
    */
    // Compose has Local*Manager to use!!!

    val isTv: Boolean
    val configureIntent: (Context) -> PendingIntent
    val activity: ActivityManager
    val clipboard: ClipboardManager
    val connectivity: ConnectivityManager
    val notification: NotificationManager
    val user: UserManager
    val uiMode: UiModeManager
    val power: PowerManager
    val wifi: WifiManager
    val packageManager: PackageManager

    val cacheDir: File
    val filesDir: File
    val externalAssetsDir: File
    val noBackupFilesDir: File
    fun getDatabasePath(name: String): File

    fun setTheme(@StyleRes id: Int)

    fun getString(@StringRes id: Int): String
    fun getString(@StringRes id: Int, vararg formatArgs: Any): String

    fun getText(@StringRes id: Int): CharSequence

    fun startService()
    fun reloadService()
    fun stopService()

}
