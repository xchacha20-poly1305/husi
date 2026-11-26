package fr.husi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Checkbox
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.database.DataStore
import fr.husi.database.preference.setBlocking
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.Logs
import fr.husi.plugin.Plugins
import fr.husi.plugin.loadString
import fr.husi.resources.Res
import fr.husi.resources.add
import fr.husi.resources.copyright
import fr.husi.resources.custom_plugin_approved_ids
import fr.husi.resources.custom_plugin_approved_ids_add
import fr.husi.resources.custom_plugin_approved_ids_input_hint
import fr.husi.resources.custom_plugin_approved_ids_installed
import fr.husi.resources.custom_plugin_approved_ids_manual
import fr.husi.resources.custom_plugin_approved_ids_summary
import fr.husi.resources.custom_plugin_prefix
import fr.husi.resources.custom_plugin_prefix_summary
import fr.husi.resources.delete
import fr.husi.resources.ok
import fr.husi.resources.security
import fr.husi.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private val PLUGIN_ID_COMPARATOR = compareBy<String> { PluginEntry.find(it) != null }
    .thenBy { it }

private data class InstalledPluginOption(
    val pluginId: String,
    val packageName: String,
    val provider: String,
    val version: String,
)

internal actual fun LazyListScope.platformPluginPreferences(
    isCustomPluginPrefixEnabled: Boolean,
    needRestart: () -> Unit,
) {
    preferenceGroup(key = "approved_custom_plugins") {
        ApprovedCustomPluginsPreference()
    }
    if (!isCustomPluginPrefixEnabled) return
    preferenceGroup(key = "custom_plugin_prefix") {
        val value by DataStore.customPluginPrefix.collectAsStateWithLifecycle()
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.customPluginPrefix.setBlocking(it)
                needRestart()
            },
            title = { Text(stringResource(Res.string.custom_plugin_prefix)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.copyright,
                    color = IconMaskColors.IconCoral,
                )
            },
            summary = { Text(stringResource(Res.string.custom_plugin_prefix_summary)) },
            valueToText = { it },
        )
    }
}

@Composable
private fun ApprovedCustomPluginsPreference() {
    val approvedIds by DataStore.approvedCustomPluginIds.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Preference(
        title = { Text(stringResource(Res.string.custom_plugin_approved_ids)) },
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
        summary = {
            val summaryText = if (approvedIds.isEmpty()) {
                stringResource(Res.string.custom_plugin_approved_ids_summary)
            } else {
                approvedIds.sortedWith(PLUGIN_ID_COMPARATOR).joinToString()
            }
            Text(summaryText)
        },
        onClick = { showDialog = true },
    )

    if (showDialog) {
        ApprovedCustomPluginsDialog(
            approvedIds = approvedIds,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ApprovedCustomPluginsDialog(
    approvedIds: Set<String>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var manualInput by rememberSaveable { mutableStateOf("") }
    val installedPlugins by produceState(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            loadInstalledPluginOptions()
        }
    }
    val installedIds = remember(installedPlugins) {
        installedPlugins.mapTo(HashSet()) { it.pluginId }
    }
    val manualIds = remember(approvedIds, installedIds) {
        approvedIds.filter { it !in installedIds }.sortedWith(PLUGIN_ID_COMPARATOR)
    }

    fun updateApprovedIds(transform: (Set<String>) -> Set<String>) {
        val next = transform(approvedIds)
        scope.launch {
            DataStore.approvedCustomPluginIds.set(next)
        }
    }

    fun addManualId() {
        val trimmed = manualInput.trim()
        if (trimmed.isEmpty()) return
        updateApprovedIds { it + trimmed }
        manualInput = ""
    }

    ScrollableDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(stringResource(Res.string.ok), onClick = onDismiss)
        },
        title = { Text(stringResource(Res.string.custom_plugin_approved_ids)) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(stringResource(Res.string.custom_plugin_approved_ids_input_hint))
                    },
                    keyboardActions = KeyboardActions { addManualId() },
                    singleLine = true,
                )
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.add),
                    contentDescription = stringResource(Res.string.custom_plugin_approved_ids_add),
                    enabled = manualInput.isNotBlank(),
                    onClick = { addManualId() },
                )
            }

            if (installedPlugins.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.custom_plugin_approved_ids_installed),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                for (plugin in installedPlugins) {
                    val checked = plugin.pluginId in approvedIds
                    val toggle = {
                        updateApprovedIds { current ->
                            if (checked) {
                                current - plugin.pluginId
                            } else {
                                current + plugin.pluginId
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { toggle() },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = plugin.pluginId,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${plugin.packageName} (${plugin.provider}, v${plugin.version})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (manualIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.custom_plugin_approved_ids_manual),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                for (id in manualIds) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = id,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                            onClick = {
                                updateApprovedIds { current ->
                                    current - id
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private suspend fun loadInstalledPluginOptions(): List<InstalledPluginOption> {
    PackageCache.awaitLoad()
    return buildList {
        for ((packageName, plugin) in PackageCache.installedPluginPackages) {
            try {
                val id = plugin.providers?.firstOrNull()?.loadString(Plugins.METADATA_KEY_ID)
                if (id.isNullOrBlank()) {
                    continue
                }
                add(
                    InstalledPluginOption(
                        pluginId = id,
                        packageName = packageName,
                        provider = Plugins.displayExeProvider(packageName),
                        version = plugin.versionName ?: "unknown",
                    ),
                )
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }.sortedWith(compareBy(PLUGIN_ID_COMPARATOR) { it.pluginId })
}
