package io.nekohasekai.sagernet

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.StrictMode
import android.os.UserManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import go.Seq
import io.nekohasekai.sagernet.bg.DefaultNetworkMonitor
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.isExpert
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.systemCertificates
import io.nekohasekai.sagernet.ktx.toStringIterator
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.utils.CrashHandler
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import libcore.Libcore
import libcore.StringIterator
import moe.matsuri.nb4a.utils.JavaUtil
import java.io.File
import androidx.work.Configuration as WorkConfiguration

class SagerNet : Application(),
    WorkConfiguration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        application = this
    }

    val externalAssets: File by lazy { getExternalFilesDir(null) ?: filesDir }
    val process: String = JavaUtil.getProcessName()
    private val isMainProcess = process == BuildConfig.APPLICATION_ID
    private val isBgProcess = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isMainProcess || isBgProcess) {
            runOnDefaultDispatcher {
                PackageCache.register()
            }
        }

        Seq.setContext(this)
        updateNotificationChannels()

        // init core
        externalAssets.mkdirs()
        Libcore.initCore(
            process,
            cacheDir.absolutePath + "/",
            filesDir.absolutePath + "/",
            externalAssets.absolutePath + "/",
            DataStore.logBufSize,
            DataStore.logLevel,
            DataStore.rulesProvider == 0,
            isExpert,
        )

        var enableCazilla = false
        var certList: StringIterator? = null
        when (DataStore.certProvider) {
            CertProvider.SYSTEM -> {}
            CertProvider.MOZILLA -> enableCazilla = true
            CertProvider.SYSTEM_AND_USER -> certList = systemCertificates.let {
                it.toStringIterator(it.size)
            }
        }
        Libcore.updateRootCACerts(enableCazilla, certList)

        if (isMainProcess) runOnDefaultDispatcher {
            runCatching {
                SubscriptionUpdater.reconfigureUpdater()
            }
        }

        if (isMainProcess) {
            Theme.apply(this)
            Theme.applyNightTheme()
            runOnDefaultDispatcher {
                DefaultNetworkMonitor.start()
            }
        }

        if (isBgProcess) {
            if (DataStore.memoryLimit) Libcore.setMemoryLimit()
        }

        if (BuildConfig.DEBUG) StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannels()
    }

    override val workManagerConfiguration: androidx.work.Configuration
        get() {
            return WorkConfiguration.Builder()
                .setDefaultProcessName(BuildConfig.APPLICATION_ID)
                .build()
        }

    @SuppressLint("InlinedApi")
    companion object {

        // Use ktx.app instead of this
        lateinit var application: SagerNet

        val isTv by lazy {
            uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(
                    it,
                    0,
                    Intent(
                        application, MainActivity::class.java
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
            }
        }
        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val user by lazy { application.getSystemService<UserManager>()!! }
        val uiMode by lazy { application.getSystemService<UiModeManager>()!! }
        val power by lazy { application.getSystemService<PowerManager>()!! }
        val wifi by lazy { application.getSystemService<WifiManager>()!! }
        val inputMethod by lazy { application.getSystemService<InputMethodManager>()!! }

        fun getClipboardText(): String {
            return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString() ?: ""
        }

        fun trySetPrimaryClip(clip: String) = try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, clip))
            true
        } catch (e: RuntimeException) {
            Logs.w(e)
            false
        }

        fun updateNotificationChannels() {
            if (Build.VERSION.SDK_INT >= 26) @RequiresApi(26) {
                notification.createNotificationChannels(
                    listOf(
                        NotificationChannel(
                            "service-vpn",
                            application.getText(R.string.service_vpn),
                            if (Build.VERSION.SDK_INT >= 28) NotificationManager.IMPORTANCE_MIN
                            else NotificationManager.IMPORTANCE_LOW
                        ),   // #1355
                        NotificationChannel(
                            "service-proxy",
                            application.getText(R.string.service_proxy),
                            NotificationManager.IMPORTANCE_LOW
                        ), NotificationChannel(
                            "service-subscription",
                            application.getText(R.string.service_subscription),
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                    )
                )
            }
        }

        fun startService() = ContextCompat.startForegroundService(
            application, Intent(application, SagerConnection.serviceClass)
        )

        fun reloadService() =
            application.sendBroadcast(Intent(Action.RELOAD).setPackage(application.packageName))

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))

    }


}
