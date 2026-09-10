@file:OptIn(ExperimentalAtomicApi::class)

package fr.husi.utils

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import fr.husi.ktx.listenForPackageChanges
import fr.husi.plugin.Plugins
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object PackageCache {
    lateinit var packageManager: PackageManager
    lateinit var selfPackageName: String

    lateinit var installedPackages: Map<String, PackageInfo>
    lateinit var installedPluginPackages: Map<String, PackageInfo>
    lateinit var installedApps: Map<String, ApplicationInfo>
    lateinit var packageMap: Map<String, Int>
    val uidMap = HashMap<Int, HashSet<String>>()
    val loaded = Mutex(true)
    private val registered = AtomicBoolean(false)

    // called from init (suspend)
    fun register(context: Context) {
        if (registered.exchange(true)) return
        val applicationContext = context.applicationContext
        packageManager = applicationContext.packageManager
        selfPackageName = applicationContext.packageName
        reload()
        applicationContext.listenForPackageChanges(false) {
            reload()
            labelMap.clear()
        }
        loaded.unlock()
    }

    fun reload() {
        val rawPackageInfo = packageManager.getInstalledPackages(
            PackageManager.MATCH_UNINSTALLED_PACKAGES
                    or PackageManager.GET_PERMISSIONS
                    or PackageManager.GET_PROVIDERS
                    or PackageManager.GET_META_DATA,
        )

        installedPackages = rawPackageInfo.filter {
            when (it.packageName) {
                "android" -> true
                else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
            }
        }.associateBy { it.packageName }

        installedPluginPackages = rawPackageInfo.filter {
            Plugins.isPlugin(it)
        }.associateBy { it.packageName }

        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        installedApps = installed.associateBy { it.packageName }
        packageMap = installed.associate { it.packageName to it.uid }
        uidMap.clear()
        for (info in installed) {
            val uid = info.uid
            uidMap.getOrPut(uid) { HashSet() }.add(info.packageName)
        }
    }

    operator fun get(uid: Int) = uidMap[uid]
    operator fun get(packageName: String) = packageMap[packageName]

    suspend fun awaitLoad() {
        if (::packageMap.isInitialized) {
            return
        }
        loaded.withLock {
            // just await
        }
    }

    fun awaitLoadSync() {
        if (::packageMap.isInitialized) {
            return
        }
        if (!registered.load()) {
            error("PackageCache.register(context) must be called before awaitLoadSync()")
        }
        runBlocking {
            loaded.withLock {
                // just await
            }
        }
    }

    private val labelMap = mutableMapOf<String, String>()
    fun loadLabel(packageManager: PackageManager, packageName: String): String {
        var label = labelMap[packageName]
        if (label != null) return label
        val info = installedApps[packageName] ?: return packageName
        label = info.loadLabel(packageManager).toString()
        labelMap[packageName] = label
        return label
    }

}
