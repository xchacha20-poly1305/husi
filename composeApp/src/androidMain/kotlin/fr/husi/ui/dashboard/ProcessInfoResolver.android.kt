package fr.husi.ui.dashboard

import fr.husi.ktx.emptyAsNull
import fr.husi.utils.PackageResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal actual class ProcessInfoResolver actual constructor() {
    private val processLabelAccess = Mutex()
    private val processLabelCache = mutableMapOf<String, String>()
    private val processIconCache = mutableMapOf<String, Any>()
    private val processIconAccess = Mutex()

    actual suspend fun resolve(process: String?, uid: Int): ProcessInfo? {
        if (process.isNullOrBlank() && uid < 0) return null
        PackageResolver.awaitLoad()
        val packageName = resolvePackageName(process, uid) ?: return null
        if (!PackageResolver.isAppInstalled(packageName)) return null
        val label = processLabelAccess.withLock {
            processLabelCache[packageName]
                ?: PackageResolver.loadAppLabel(packageName)
                    ?.also { processLabelCache[packageName] = it }
        } ?: return null
        val icon = processIconAccess.withLock {
            processIconCache[packageName]
                ?: PackageResolver.loadAppIcon(packageName)
                    ?.also { processIconCache[packageName] = it }
        }
        return ProcessInfo(packageName = packageName, label = label, icon = icon)
    }

    actual fun clear() {
        runBlocking {
            processLabelAccess.withLock {
                processLabelCache.clear()
            }
            processIconAccess.withLock {
                processIconCache.clear()
            }
        }
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
