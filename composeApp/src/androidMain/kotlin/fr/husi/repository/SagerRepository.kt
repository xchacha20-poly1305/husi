package fr.husi.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import fr.husi.Action
import fr.husi.bg.SagerConnection
import fr.husi.libcore.createBoxService
import fr.husi.resources.Res
import fr.husi.resources.service_proxy
import fr.husi.resources.service_subscription
import fr.husi.resources.service_vpn
import fr.husi.ui.MainActivity
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import java.io.File
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

@OptIn(ExperimentalResourceApi::class)
open class SagerRepository(
    override val context: Context,
    override val isMainProcess: Boolean,
    override val isBgProcess: Boolean,
) : AndroidRepository {

    init {
        // We don't know when could the resource be initialized
        setResourceReaderAndroidContext(context)
    }

    override val isAndroid = true
    override val isLinux = false
    override val isMacOs = false
    override val isWindows = false

    override val boxService: fr.husi.libcore.Service? by lazy {
        createBoxService(isBgProcess)
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

    override val connectivity: ConnectivityManager by lazy {
        serviceContext.getSystemService<ConnectivityManager>()!!
    }

    override val user: UserManager by lazy {
        serviceContext.getSystemService<UserManager>()!!
    }

    private val uiMode: UiModeManager by lazy {
        serviceContext.getSystemService<UiModeManager>()!!
    }

    override val power: PowerManager by lazy {
        serviceContext.getSystemService<PowerManager>()!!
    }

    override val wifi: WifiManager by lazy {
        serviceContext.getSystemService<WifiManager>()!!
    }

    override val packageManager: PackageManager = serviceContext.packageManager

    private val notification: NotificationManager by lazy {
        serviceContext.getSystemService<NotificationManager>()!!
    }

    override val cacheDir: File by lazy {
        serviceContext.cacheDir.apply { mkdirs() }
    }

    override val filesDir: File by lazy {
        serviceContext.filesDir.apply { mkdirs() }
    }

    override val externalAssetsDir: File by lazy {
        (serviceContext.getExternalFilesDir(null) ?: filesDir).apply { mkdirs() }
    }

    override fun getDatabasePath(name: String): File {
        return serviceContext.getDatabasePath(name)
    }

    override val noBackupFilesDir: File by lazy {
        serviceContext.noBackupFilesDir
    }

    override val isTv: Boolean
        get() = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    override suspend fun getString(resource: StringResource): String {
        return getComposeString(resource)
    }

    override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
        return getComposeString(resource, *formatArgs)
    }

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String {
        return getComposePluralString(resource, quantity, *formatArgs)
    }

    override fun startService() {
        ContextCompat.startForegroundService(
            serviceContext,
            Intent(context, SagerConnection.serviceClass),
        )
    }

    override fun reloadService() {
        serviceContext.sendBroadcast(
            Intent(Action.RELOAD).setPackage(serviceContext.packageName),
        )
    }

    override fun stopService() {
        serviceContext.sendBroadcast(
            Intent(Action.CLOSE).setPackage(serviceContext.packageName),
        )
    }

    override suspend fun updateNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val importanceVpn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NotificationManager.IMPORTANCE_MIN
        } else {
            NotificationManager.IMPORTANCE_LOW
        }

        val channels = listOf(
            NotificationChannel(
                "service-vpn",
                getString(Res.string.service_vpn),
                importanceVpn,
            ),
            NotificationChannel(
                "service-proxy",
                getString(Res.string.service_proxy),
                NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                "service-subscription",
                getString(Res.string.service_subscription),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        notification.createNotificationChannels(channels)
    }
}
