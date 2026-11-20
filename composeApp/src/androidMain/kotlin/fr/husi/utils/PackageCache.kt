@file:OptIn(ExperimentalAtomicApi::class)

package fr.husi.utils

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import fr.husi.ktx.listenForPackageChanges
import fr.husi.plugin.Plugins
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object PackageCache {
    private data class Snapshot(
        val installedPackages: Map<String, PackageInfo>,
        val installedPluginPackages: Map<String, PackageInfo>,
        val installedApps: Map<String, ApplicationInfo>,
        val packageMap: Map<String, Int>,
        val uidMap: Map<Int, Set<String>>,
    )

    lateinit var packageManager: PackageManager
    lateinit var selfPackageName: String

    private val snapshot = MutableStateFlow<Snapshot?>(null)
    private val registered = AtomicBoolean(false)
    private val reloadLock = Any()
    private val labelMap = ConcurrentHashMap<String, String>()

    val installedPackages: Map<String, PackageInfo>
        get() = snapshot.value?.installedPackages.orEmpty()

    val installedPluginPackages: Map<String, PackageInfo>
        get() = snapshot.value?.installedPluginPackages.orEmpty()

    val installedApps: Map<String, ApplicationInfo>
        get() = snapshot.value?.installedApps.orEmpty()

    val packageMap: Map<String, Int>
        get() = snapshot.value?.packageMap.orEmpty()

    val uidMap: Map<Int, Set<String>>
        get() = snapshot.value?.uidMap.orEmpty()

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
    }

    fun reload() {
        synchronized(reloadLock) {
            val rawPackageInfo = packageManager.getInstalledPackages(
                PackageManager.MATCH_UNINSTALLED_PACKAGES
                        or PackageManager.GET_PERMISSIONS
                        or PackageManager.GET_PROVIDERS
                        or PackageManager.GET_META_DATA,
            )

            val newInstalledPackages = rawPackageInfo.filter {
                when (it.packageName) {
                    "android" -> true
                    else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                }
            }.associateBy { it.packageName }

            val newInstalledPluginPackages = rawPackageInfo.filter {
                Plugins.isPlugin(it)
            }.associateBy { it.packageName }

            val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val newInstalledApps = installed.associateBy { it.packageName }
            val newPackageMap = installed.associate { it.packageName to it.uid }
            val newUidMap = HashMap<Int, HashSet<String>>()
            for (info in installed) {
                newUidMap.getOrPut(info.uid) { HashSet() }.add(info.packageName)
            }

            snapshot.value = Snapshot(
                installedPackages = newInstalledPackages,
                installedPluginPackages = newInstalledPluginPackages,
                installedApps = newInstalledApps,
                packageMap = newPackageMap,
                uidMap = newUidMap,
            )
        }
    }

    operator fun get(uid: Int): Set<String>? = snapshot.value?.uidMap?.get(uid)
    operator fun get(packageName: String): Int? = snapshot.value?.packageMap?.get(packageName)

    suspend fun awaitLoad() {
        snapshot.filterNotNull().first()
    }

    fun awaitLoadSync() {
        if (snapshot.value != null) {
            return
        }
        if (!registered.load()) {
            error("PackageCache.register(context) must be called before awaitLoadSync()")
        }
        runBlocking {
            awaitLoad()
        }
    }

    fun loadLabel(packageManager: PackageManager, packageName: String): String {
        labelMap[packageName]?.let { return it }
        val info = snapshot.value?.installedApps?.get(packageName) ?: return packageName
        val label = info.loadLabel(packageManager).toString()
        labelMap[packageName] = label
        return label
    }

}
