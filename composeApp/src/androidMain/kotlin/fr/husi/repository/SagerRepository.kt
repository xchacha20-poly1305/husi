package fr.husi.repository

import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import fr.husi.Action
import fr.husi.bg.SagerConnection
import fr.husi.libcore.createBoxService
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

open class SagerRepository(
    override val context: Context,
    override val isMainProcess: Boolean,
    override val isBgProcess: Boolean,
) : AndroidRepository {

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
                Intent(serviceContext, Class.forName("fr.husi.ui.MainActivity"))
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
            Intent(androidRepo.context, SagerConnection.serviceClass),
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
}
