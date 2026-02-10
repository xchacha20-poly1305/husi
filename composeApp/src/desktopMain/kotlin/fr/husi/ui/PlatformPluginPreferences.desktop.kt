package fr.husi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceType
import fr.husi.database.PluginEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.desktop_plugins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

internal actual fun LazyListScope.desktopPluginPreferences() {
    item("desktop_plugins_category", PreferenceType.CATEGORY) {
        PreferenceCategory(text = { Text(stringResource(Res.string.desktop_plugins)) })
    }
    item("desktop_plugins_list") {
        DesktopPluginPreferences()
    }
}

@Composable
private fun DesktopPluginPreferences() {
    val scope = rememberCoroutineScope()
    val plugins by SagerDatabase.pluginDao.getAll().collectAsStateWithLifecycle(emptyList())
    val pluginMap = remember(plugins) { plugins.associateBy { it.pluginId } }
    val knownEntries = remember { enumValues<PluginEntry>().toList() }

    Column {
        for (entry in knownEntries) {
            val current = pluginMap[entry.pluginId]
            PluginPathPreference(
                pluginId = entry.pluginId,
                displayName = stringResource(entry.displayName),
                current = current,
                onUpdate = { pluginId, plugin ->
                    scope.launch(Dispatchers.IO) {
                        upsertPlugin(pluginId, plugin)
                    }
                },
            )
        }
    }
}

@Composable
private fun PluginPathPreference(
    pluginId: String,
    displayName: String,
    current: PluginEntity?,
    onUpdate: (String, PluginEntity?) -> Unit,
) {
    val path = current?.path.orEmpty()
    TextFieldPreference(
        value = path,
        onValueChange = { newPath ->
            val trimmed = newPath.trim()
            if (trimmed.isBlank()) {
                onUpdate(pluginId, null)
            } else {
                onUpdate(
                    pluginId,
                    PluginEntity(
                        pluginId = pluginId,
                        path = trimmed,
                    ),
                )
            }
        },
        title = { Text(displayName) },
        summary = { Text(contentOrUnset(path)) },
        textToValue = { it },
        icon = { Spacer(Modifier.size(24.dp)) },
        valueToText = { it },
    )
}

private suspend fun upsertPlugin(pluginId: String, plugin: PluginEntity?) {
    if (plugin == null) {
        SagerDatabase.pluginDao.delete(pluginId)
    } else {
        SagerDatabase.pluginDao.upsert(plugin)
    }
}
