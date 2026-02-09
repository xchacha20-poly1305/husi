package fr.husi

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import androidx.core.content.getSystemService
import fr.husi.bg.AppChangeReceiver
import fr.husi.bg.DefaultNetworkMonitor
import fr.husi.bg.SubscriptionUpdater
import fr.husi.database.DataStore
import fr.husi.ktx.isExpert
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.systemCertificates
import fr.husi.ktx.toStringIterator
import fr.husi.libcore.Libcore
import fr.husi.libcore.StringIterator
import fr.husi.repository.SagerRepository
import fr.husi.repository.repo
import fr.husi.utils.CrashHandler
import fr.husi.utils.PackageCache
import go.Seq
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import java.io.File
import androidx.work.Configuration as WorkConfiguration

class Application : Application(),
    WorkConfiguration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        repo = SagerRepository(this, isMainProcess, isBgProcess)
    }

    val externalAssets: File by lazy { getExternalFilesDir(null) ?: filesDir }
    private val process = tryGetProcessName()
    val isMainProcess = process == BuildConfig.APPLICATION_ID
    val isBgProcess = process.endsWith(":bg")

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
            DataStore.logMaxLine,
            DataStore.logLevel,
            DataStore.rulesProvider == 0,
            isExpert,
        )

        var certOption = 0
        var certList: StringIterator? = null
        when (DataStore.certProvider) {
            CertProvider.SYSTEM -> {
                certOption = Libcore.CertGoOrigin
            }

            CertProvider.MOZILLA -> {
                certOption = Libcore.CertMozilla
            }

            CertProvider.SYSTEM_AND_USER -> {
                certOption = Libcore.CertWithUserTrust
                certList = systemCertificates.let {
                    it.toStringIterator(it.size)
                }
            }

            CertProvider.CHROME -> {
                certOption = Libcore.CertChrome
            }
        }
        Libcore.updateRootCACerts(certOption, certList)

        if (isMainProcess) runOnDefaultDispatcher {
            runCatching {
                SubscriptionUpdater.reconfigureUpdater()
            }
            registerReceiver(
                AppChangeReceiver(),
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addDataScheme("package")
                },
            )
        }

        if (isMainProcess) {
            runOnDefaultDispatcher {
                DefaultNetworkMonitor.start()
            }
        }

        if (isBgProcess) {
            repo.boxService?.start()
            if (DataStore.memoryLimit) Libcore.setMemoryLimit()
        }

        if (BuildConfig.DEBUG) StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannels()
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setDefaultProcessName(BuildConfig.APPLICATION_ID)
            .build()

    @SuppressLint("PrivateApi")
    private fun tryGetProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return getProcessName()

        // Using the same technique as Application.getProcessName() for older devices
        // Using reflection since ActivityThread is an internal API
        try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val methodName = "currentProcessName"
            val getProcessName = activityThread.getDeclaredMethod(methodName)
            return getProcessName.invoke(null) as String
        } catch (_: Exception) {
            return BuildConfig.APPLICATION_ID
        }
    }

    private val notification by lazy { getSystemService<NotificationManager>()!! }

    private fun updateNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val importanceVpn =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) NotificationManager.IMPORTANCE_MIN
            else NotificationManager.IMPORTANCE_LOW

        val channels = listOf(
            NotificationChannel(
                "service-vpn",
                getText(R.string.service_vpn),
                importanceVpn,
            ),
            NotificationChannel(
                "service-proxy",
                getText(R.string.service_proxy),
                NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                "service-subscription",
                getText(R.string.service_subscription),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        notification.createNotificationChannels(channels)
    }
}
