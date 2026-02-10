package fr.husi.plugin

import fr.husi.database.SagerDatabase
import fr.husi.repository.repo
import kotlinx.coroutines.runBlocking
import java.io.File

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

        val file = File(rawPath).absoluteFile
        val lastModified = file.lastModified()
        cache[pluginId]?.let { cached ->
            if (cached.path == file.absolutePath &&
                cached.lastModified == lastModified
            ) {
                return cached.path
            }
        }

        if (!file.exists() || !file.isFile) {
            cache.remove(pluginId)
            return null
        }
        if (!isExecutable(file)) {
            cache.remove(pluginId)
            return null
        }

        val resolved = file.absolutePath
        cache[pluginId] = CacheEntry(
            path = resolved,
            lastModified = lastModified,
        )
        return resolved
    }

    private fun isExecutable(file: File): Boolean {
        if (repo.isWindows) return true
        return file.canExecute()
    }

}
