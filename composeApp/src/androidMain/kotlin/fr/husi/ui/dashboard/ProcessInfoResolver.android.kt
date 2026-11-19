package fr.husi.ui.dashboard

import fr.husi.ktx.emptyAsNull
import fr.husi.utils.PackageResolver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal actual class ProcessInfoResolver actual constructor() {
    private class CacheSnapshot(
        val labels: ConcurrentHashMap<String, String> = ConcurrentHashMap(),
        val icons: ConcurrentHashMap<String, Any> = ConcurrentHashMap(),
    )

    private val cacheRef = AtomicReference(CacheSnapshot())

    actual suspend fun resolve(process: String?, uid: Int): ProcessInfo? {
        if (process.isNullOrBlank() && uid < 0) return null
        PackageResolver.awaitLoad()
        val packageName = resolvePackageName(process, uid) ?: return null
        if (!PackageResolver.isAppInstalled(packageName)) return null
        val snapshot = cacheRef.get()
        val label = snapshot.labels[packageName]
            ?: PackageResolver.loadAppLabel(packageName)?.let { loaded ->
                snapshot.labels.putIfAbsent(packageName, loaded) ?: loaded
            }
            ?: return null
        val icon = snapshot.icons[packageName]
            ?: PackageResolver.loadAppIcon(packageName)?.let { loaded ->
                snapshot.icons.putIfAbsent(packageName, loaded) ?: loaded
            }
        return ProcessInfo(packageName = packageName, label = label, icon = icon)
    }

    actual fun clear() {
        cacheRef.set(CacheSnapshot())
    }

    private fun resolvePackageName(process: String?, uid: Int): String? {
        process.emptyAsNull()?.let { packageName ->
            if (PackageResolver.isAppInstalled(packageName)) {
                return packageName
            }
        }
        if (uid >= 0) {
            return PackageResolver.findPackagesForUid(uid)?.firstOrNull()
        }
        return null
    }
}
