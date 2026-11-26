package fr.husi.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.RadioButton
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.apps
import fr.husi.resources.cancel
import fr.husi.resources.custom_plugin_approved_ids_empty
import fr.husi.resources.custom_plugin_not_approved_hint
import fr.husi.resources.custom_plugin_not_approved_summary
import fr.husi.resources.custom_plugin_plugin_id
import fr.husi.resources.not_set
import fr.husi.resources.ok
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun CustomPluginExecutableRow(
    pluginId: String,
    executablePath: String,
    onPluginIdChange: (String) -> Unit,
    onExecutablePathChange: (String) -> Unit,
) {
    val approvedIds by DataStore.approvedCustomPluginIds.collectAsStateWithLifecycle()
    val trimmedId = pluginId.trim()
    val isUnapproved = trimmedId.isNotEmpty() && trimmedId !in approvedIds
    var openDialog by rememberSaveable { mutableStateOf(false) }

    Preference(
        title = { Text(stringResource(Res.string.custom_plugin_plugin_id)) },
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconLightBlue)
        },
        summary = {
            when {
                trimmedId.isEmpty() -> Text(stringResource(Res.string.not_set))
                isUnapproved -> Text(
                    text = stringResource(Res.string.custom_plugin_not_approved_summary, trimmedId),
                    color = MaterialTheme.colorScheme.error,
                )

                else -> Text(trimmedId)
            }
        },
        onClick = { openDialog = true },
    )

    if (openDialog) {
        PluginIdDialog(
            initialPluginId = trimmedId,
            approvedIds = approvedIds,
            onDismiss = { openDialog = false },
            onConfirm = { newId ->
                onPluginIdChange(newId.trim())
                openDialog = false
            },
        )
    }
}

@Composable
private fun PluginIdDialog(
    initialPluginId: String,
    approvedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val options = remember(approvedIds, initialPluginId) {
        buildList {
            if (initialPluginId.isNotEmpty() && initialPluginId !in approvedIds) {
                add(initialPluginId)
            }
            addAll(approvedIds.sorted())
        }
    }
    var selectedId by rememberSaveable { mutableStateOf(initialPluginId) }

    ScrollableDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (options.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.ok))
                }
            } else {
                TextButton(
                    onClick = { onConfirm(selectedId) },
                    enabled = selectedId.isNotEmpty(),
                ) {
                    Text(stringResource(Res.string.ok))
                }
            }
        },
        dismissButton = if (options.isEmpty()) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
        title = { Text(stringResource(Res.string.custom_plugin_plugin_id)) },
    ) {
        if (options.isEmpty()) {
            Text(
                text = stringResource(Res.string.custom_plugin_approved_ids_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                for (id in options) {
                    val selected = selectedId == id
                    val isApproved = id in approvedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selected,
                                onClick = { selectedId = id },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = id,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (!isApproved) {
                                Text(
                                    text = stringResource(Res.string.custom_plugin_not_approved_hint),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
