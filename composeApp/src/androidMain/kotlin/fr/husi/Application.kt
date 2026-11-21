package fr.husi

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import fr.husi.bg.AppChangeReceiver
import fr.husi.bg.DefaultNetworkMonitor
import fr.husi.bg.ProxyService
import fr.husi.bg.RouteAssetUpdater
import fr.husi.bg.ServiceNotification
import fr.husi.bg.ServiceRegistry
import fr.husi.bg.ServiceState
import fr.husi.bg.SubscriptionUpdater
import fr.husi.bg.VpnService
import fr.husi.bg.migrateCustomRouteAssets
import fr.husi.compose.clearClipboardImageCache
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.database.preference.getBlocking
import fr.husi.di.initHusiKoin
import fr.husi.ktx.Logs
import fr.husi.ktx.invariantDirectoryPathString
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.ktx.runOnMainDispatcher
import fr.husi.libcore.Libcore
import fr.husi.libcore.loadCA
import fr.husi.repository.AndroidRepository
import fr.husi.repository.SagerRepository
import fr.husi.tailscale.TaildropSendService
import fr.husi.utils.CrashHandler
import fr.husi.utils.PackageCache
import fr.husi.utils.installBundledRuleSets
import go.Seq
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import androidx.work.Configuration as WorkConfiguration

class Application : Application(),
    WorkConfiguration.Provider {

    private lateinit var repository: AndroidRepository

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        repository = SagerRepository(this, isMainProcess, isBgProcess)
    }

    val externalAssets: PlatformFile by lazy { PlatformFile(getExternalFilesDir(null) ?: filesDir) }
    private val appId by lazy { packageName }
    private val process by lazy { tryGetProcessName() }
    val isMainProcess get() = process == appId
    val isBgProcess get() = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()
        initHusiKoin(repository)

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isBgProcess) {
            runOnMainDispatcher {
                delay(500.milliseconds)
                clearStaleServiceIfNeeded()
            }
        }

        if (isMainProcess) runOnIoDispatcher {
            clearClipboardImageCache(PlatformFile(cacheDir))
        }

        if (isMainProcess) TaildropSendService.observe(this)

        if (isMainProcess) runOnDefaultDispatcher {
            // The component state may drift from the preference, e.g. after a backup restore.
            val hidden = DataStore.hideLauncherIcon.get()
            if (LauncherIcon.hidden != hidden) LauncherIcon.hidden = hidden
        }

        if (isMainProcess || isBgProcess) {
            runOnDefaultDispatcher {
                PackageCache.register(this@Application)
            }
        }

        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }

        externalAssets.createDirectories()
        val isDebug = runBlocking { DataStore.advancedFeature(Key.ADVANCED_DEBUG) } != null
        if (!isDebug) {
            Logs.openFile(externalAssets.resolve(LOG_FILE_NAME), DataStore.logLevel.getBlocking())
        }
        runBlocking {
            if (isBgProcess && DataStore.rulesProvider.get() == RuleProvider.OFFICIAL) {
                installBundledRuleSets()
            }
            migrateCustomRouteAssets(
                externalAssets,
                SagerDatabase.assetDao.getAll().first().map { it.name },
            )
        }
        if (isBgProcess) initCore(isDebug)

        if (isMainProcess) runOnDefaultDispatcher {
            runCatching {
                SubscriptionUpdater.reconfigureUpdater()
                RouteAssetUpdater.reconfigureUpdater()
            }
            registerReceiver(
                AppChangeReceiver(),
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addDataScheme("package")
                },
            )
        }

        if (isBgProcess) {
            runBlocking {
                DefaultNetworkMonitor.start()
            }
            repository.boxService?.start()
        }

        if (isDebug) StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }

    private fun clearStaleServiceIfNeeded() {
        val activeState = ServiceRegistry.baseService?.data?.state
        if (activeState != null && activeState != ServiceState.Stopped) return

        val hasStaleNotification = getSystemService<NotificationManager>()
            ?.activeNotifications
            ?.any { it.id == ServiceNotification.NOTIFICATION_ID } == true
        if (!hasStaleNotification) return

        stopService(Intent(this, VpnService::class.java))
        stopService(Intent(this, ProxyService::class.java))
        NotificationManagerCompat.from(this).cancel(ServiceNotification.NOTIFICATION_ID)
    }

    private fun initCore(isDebug: Boolean) {
        Seq.setContext(this)
        Libcore.initCore(
            PlatformFile(cacheDir).invariantDirectoryPathString(),
            PlatformFile(filesDir).invariantDirectoryPathString(),
            externalAssets.invariantDirectoryPathString(),
            DataStore.logMaxLine.getBlocking(),
            DataStore.logLevel.getBlocking(),
            isDebug,
        )
        Logs.attachCore()
        loadCA(DataStore.certProvider.getBlocking())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setDefaultProcessName(appId)
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
            return appId
        }
    }

}
