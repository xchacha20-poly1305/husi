package fr.husi.ui.dashboard

import fr.husi.ktx.blankAsNull
import fr.husi.utils.appicon.DesktopAppIcons
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal actual class ProcessInfoResolver(
    private val lookup: (String) -> ProcessInfo?,
) {
    actual constructor() : this(DesktopAppIcons::resolve)

    private class CacheEntry(val info: ProcessInfo?)

    private val cacheRef = AtomicReference(ConcurrentHashMap<String, CacheEntry>())

    actual suspend fun resolve(process: String?, uid: Int): ProcessInfo? {
        val path = process?.trim().blankAsNull() ?: return null
        val currentCache = cacheRef.get()
        currentCache[path]?.let { return it.info }
        val resolved = lookup(path)
        return currentCache.putIfAbsent(path, CacheEntry(resolved))?.info ?: resolved
    }

    actual fun clear() {
        cacheRef.set(ConcurrentHashMap())
    }
}
