package fr.husi.ui

import androidx.compose.runtime.Composable
import fr.husi.database.SagerDatabase
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.openFilePath
import kotlinx.coroutines.flow.first

internal actual suspend fun loadPlatformPlugins(onPlugin: suspend (PluginDisplay) -> Unit) {
    val entries = enumValues<PluginEntry>().toList()
    val plugins = SagerDatabase.pluginDao.getAll().first()
        .associateBy { it.pluginId }
    for (entry in entries) {
        val record = plugins[entry.pluginId] ?: continue
        val path = record.path.trim()
        if (path.isBlank()) continue
        val version = runCatching { entry.getVersion(path) }
            .getOrElse {
                Logs.w(it)
                "unknown"
            }
        onPlugin(
            PluginDisplay(
                id = entry.pluginId,
                packageName = "",
                version = version,
                versionCode = 0L,
                provider = "Desktop",
                entry = entry,
                path = path,
            ),
        )
    }
}

internal actual fun openPluginCard(plugin: PluginDisplay) {
    val path = plugin.path?.trim().blankAsNull() ?: return
    openFilePath(path)
}

@Composable
internal actual fun rememberShouldRequestBatteryOptimizations(): Boolean = false

internal actual fun requestIgnoreBatteryOptimizations() {}
