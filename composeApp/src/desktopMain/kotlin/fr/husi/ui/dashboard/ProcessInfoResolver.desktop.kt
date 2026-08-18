package fr.husi.ui.dashboard

import fr.husi.ktx.blankAsNull
import fr.husi.utils.appicon.DesktopAppIcons
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal actual class ProcessInfoResolver(
    private val lookup: (String) -> ProcessInfo?,
) {
    actual constructor() : this(DesktopAppIcons::resolve)

    private val cacheAccess = Mutex()
    private val cache = mutableMapOf<String, ProcessInfo?>()

    actual suspend fun resolve(process: String?, uid: Int): ProcessInfo? {
        val path = process?.trim().blankAsNull() ?: return null
        return cacheAccess.withLock {
            if (cache.containsKey(path)) {
                cache.getValue(path)
            } else {
                lookup(path).also { cache[path] = it }
            }
        }
    }

    actual fun clear() {
        runBlocking {
            cacheAccess.withLock {
                cache.clear()
            }
        }
    }
}
