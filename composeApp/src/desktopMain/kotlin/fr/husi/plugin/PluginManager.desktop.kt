package fr.husi.plugin

import fr.husi.database.SagerDatabase
import fr.husi.ktx.canExecute
import fr.husi.ktx.invariantPathString
import fr.husi.platform.PlatformInfo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.lastModified
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime

actual object PluginManager {

    private data class CacheEntry(
        val path: String,
        val lastModified: Long,
    )

    private val cache = HashMap<String, CacheEntry>()

    @Throws(Throwable::class)
    actual fun init(pluginId: String): PluginInitResult? {
        if (pluginId.isEmpty()) return null
        val resolved = resolvePath(pluginId)
        return if (resolved == null) {
            throw PluginNotFoundException(pluginId)
        } else {
            PluginInitResult(resolved)
        }
    }

    @OptIn(ExperimentalTime::class)
    @Synchronized
    private fun resolvePath(pluginId: String): String? {
        val entry = runBlocking {
            SagerDatabase.pluginDao.getById(pluginId)
        } ?: run {
            cache.remove(pluginId)
            return null
        }
        val rawPath = entry.path.trim()
        if (rawPath.isBlank()) {
            cache.remove(pluginId)
            return null
        }

        val file = PlatformFile(rawPath)
        val lastModified = file.lastModified().toEpochMilliseconds()
        val resolved = file.invariantPathString()
        cache[pluginId]?.let { cached ->
            if (cached.path == resolved &&
                cached.lastModified == lastModified
            ) {
                return cached.path
            }
        }

        if (!file.exists() || !file.isRegularFile()) {
            cache.remove(pluginId)
            return null
        }
        if (!isExecutable(file)) {
            cache.remove(pluginId)
            return null
        }

        cache[pluginId] = CacheEntry(
            path = resolved,
            lastModified = lastModified,
        )
        return resolved
    }

    private fun isExecutable(file: PlatformFile): Boolean {
        if (PlatformInfo.isWindows) return true
        return file.canExecute()
    }

}
