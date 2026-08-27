package fr.husi

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import androidx.work.Configuration as WorkConfiguration
import fr.husi.bg.AppChangeReceiver
import fr.husi.bg.DefaultNetworkMonitor
import fr.husi.bg.RouteAssetUpdater
import fr.husi.bg.SubscriptionUpdater
import fr.husi.compose.clearClipboardImageCache
import fr.husi.database.DataStore
import fr.husi.di.initHusiKoin
import fr.husi.ktx.invariantDirectoryPathString
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.libcore.Libcore
import fr.husi.libcore.loadCA
import fr.husi.repository.AndroidRepository
import fr.husi.repository.SagerRepository
import fr.husi.utils.CrashHandler
import fr.husi.utils.PackageCache
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import go.Seq
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.runBlocking

class Application : Application(),
    WorkConfiguration.Provider {

    private lateinit var repository: AndroidRepository

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        repository = SagerRepository(this, isMainProcess, isBgProcess)
    }

    private val appId by lazy { packageName }
    private val process by lazy { tryGetProcessName() }
    val isMainProcess get() = process == appId
    val isBgProcess get() = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()
        initHusiKoin(repository)

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isMainProcess) runOnIoDispatcher {
            clearClipboardImageCache(repository.cacheDir)
        }

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

        Seq.setContext(this)
        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }

        // init core
        val rulesProvider = DataStore.rulesProvider.getBlocking()
        val isExpert = DataStore.isExpert.getBlocking()
        if (isBgProcess && rulesProvider == RuleProvider.OFFICIAL) {
            runBlocking { copyBundledRuleSetAssetsIfNeeded() }
        }
        Libcore.initCore(
            isBgProcess,
            !isBgProcess,
            repository.cacheDir.invariantDirectoryPathString(),
            repository.filesDir.invariantDirectoryPathString(),
            repository.externalAssetsDir.invariantDirectoryPathString(),
            DataStore.logMaxLine.getBlocking(),
            DataStore.logLevel.getBlocking(),
            rulesProvider == 0,
            isExpert,
        )
        loadCA(DataStore.certProvider.getBlocking())

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

        if (isExpert) StrictMode.setVmPolicy(
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
