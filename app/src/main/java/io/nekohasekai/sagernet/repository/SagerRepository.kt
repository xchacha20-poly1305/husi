package io.nekohasekai.sagernet.repository

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.UserManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.bg.NativeInterface
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.ui.MainActivity
import libcore.Libcore
import java.io.File

open class SagerRepository(
    override val context: Context,
    override val isMainProcess: Boolean,
    override val isBgProcess: Boolean,
) : Repository {

    override val boxService: libcore.Service? by lazy {
        if (isBgProcess) Libcore.newService(NativeInterface(false)) else null
    }

    protected val serviceContext = context.applicationContext ?: context

    override val configureIntent: (Context) -> PendingIntent by lazy {
        { callerContext ->
            PendingIntent.getActivity(
                callerContext,
                0,
                Intent(serviceContext, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    override val activity: ActivityManager by lazy {
        serviceContext.getSystemService<ActivityManager>()!!
    }

    override val clipboard: ClipboardManager by lazy {
        serviceContext.getSystemService<ClipboardManager>()!!
    }

    override val connectivity: ConnectivityManager by lazy {
        serviceContext.getSystemService<ConnectivityManager>()!!
    }

    override val notification: NotificationManager by lazy {
        serviceContext.getSystemService<NotificationManager>()!!
    }

    override val user: UserManager by lazy {
        serviceContext.getSystemService<UserManager>()!!
    }

    override val uiMode: UiModeManager by lazy {
        serviceContext.getSystemService<UiModeManager>()!!
    }

    override val power: PowerManager by lazy {
        serviceContext.getSystemService<PowerManager>()!!
    }

    override val wifi: WifiManager by lazy {
        serviceContext.getSystemService<WifiManager>()!!
    }

    override val packageManager: PackageManager = serviceContext.packageManager

    override val cacheDir: File by lazy {
        serviceContext.cacheDir.apply { mkdirs() }
    }

    override val filesDir: File by lazy {
        serviceContext.filesDir.apply { mkdirs() }
    }

    override val externalAssetsDir: File by lazy {
        (serviceContext.getExternalFilesDir(null) ?: filesDir).apply { mkdirs() }
    }

    override fun getDatabasePath(name: String): File  {
        return serviceContext.getDatabasePath(name)
    }

    override val noBackupFilesDir: File by lazy {
        serviceContext.noBackupFilesDir
    }

    override val isTv: Boolean
        get() = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    override fun setTheme(id: Int) {
        serviceContext.setTheme(id)
    }

    private val languageContext by lazy {
        ContextCompat.getContextForLanguage(serviceContext)
    }

    override fun getString(@StringRes id: Int): String {
        return languageContext.getString(id)
    }

    override fun getString(@StringRes id: Int, vararg formatArgs: Any): String {
        return languageContext.getString(id, *formatArgs)
    }

    override fun getText(@StringRes id: Int): CharSequence {
        return languageContext.getText(id)
    }

    override fun startService() {
        ContextCompat.startForegroundService(
            serviceContext,
            Intent(serviceContext, SagerConnection.serviceClass)
        )
    }

    override fun reloadService() {
        serviceContext.sendBroadcast(
            Intent(Action.RELOAD).setPackage(serviceContext.packageName)
        )
    }

    override fun stopService() {
        serviceContext.sendBroadcast(
            Intent(Action.CLOSE).setPackage(serviceContext.packageName)
        )
    }
}
