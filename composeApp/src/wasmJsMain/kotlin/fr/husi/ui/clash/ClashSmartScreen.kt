package fr.husi.ui.clash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.cached
import fr.husi.resources.cancel
import fr.husi.resources.clash_smart
import fr.husi.resources.clash_smart_cache
import fr.husi.resources.clash_smart_collect_data
import fr.husi.resources.clash_smart_config_name
import fr.husi.resources.clash_smart_config_name_invalid
import fr.husi.resources.clash_smart_empty_fallback
import fr.husi.resources.clash_smart_expected_status
import fr.husi.resources.clash_smart_fixed_node
import fr.husi.resources.clash_smart_flush_all
import fr.husi.resources.clash_smart_flush_all_confirm
import fr.husi.resources.clash_smart_flush_config
import fr.husi.resources.clash_smart_flush_config_confirm
import fr.husi.resources.clash_smart_groups
import fr.husi.resources.clash_smart_lgbm
import fr.husi.resources.clash_smart_lightgbm
import fr.husi.resources.clash_smart_no_weights
import fr.husi.resources.clash_smart_policy_priority
import fr.husi.resources.clash_smart_prefer_asn
import fr.husi.resources.clash_smart_refresh_weights
import fr.husi.resources.clash_smart_sample_rate
import fr.husi.resources.clash_smart_tolerance
import fr.husi.resources.clash_smart_upgrade_lgbm
import fr.husi.resources.clash_unsupported
import fr.husi.resources.ok
import fr.husi.resources.refresh
import fr.husi.resources.smart_clear_fixed
import fr.husi.resources.undo
import fr.husi.resources.update
import fr.husi.ui.dashboard.formatSmartWeight
import fr.husi.ui.dashboard.smartRankLabel
import fr.husi.ui.settings.SettingsPage
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun ClashSmartScreen(
    state: ClashSmartState,
    onRefresh: () -> Unit,
    onRefreshGroupWeights: (String) -> Unit,
    onClearFixed: (String) -> Unit,
    onFlushAllCache: () -> Unit,
    onFlushConfigCache: (String) -> Unit,
    onUpgradeLgbm: () -> Unit,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configName by rememberSaveable { mutableStateOf("") }
    var showFlushAllConfirm by rememberSaveable { mutableStateOf(false) }
    var showFlushConfigConfirm by rememberSaveable { mutableStateOf(false) }

    val configValid = ClashSmartModel.isValidConfigName(configName)
    val flushAllBusy = state.pending[ClashSmartModel.ACTION_FLUSH_ALL] == true
    val flushAllUnavailable = state.unavailable[ClashSmartModel.ACTION_FLUSH_ALL] == true
    val flushConfigBusy = state.pending[ClashSmartModel.ACTION_FLUSH_CONFIG] == true
    val flushConfigUnavailable = state.unavailable[ClashSmartModel.ACTION_FLUSH_CONFIG] == true
    val upgradeLgbmBusy = state.pending[ClashSmartModel.ACTION_UPGRADE_LGBM] == true
    val upgradeLgbmUnavailable = state.unavailable[ClashSmartModel.ACTION_UPGRADE_LGBM] == true

    SettingsPage(
        title = Res.string.clash_smart,
        modifier = modifier,
        onBackPress = onBackPress,
        actions = {
            CapsuleActionButton {
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.update),
                    contentDescription = stringResource(Res.string.refresh),
                    enabled = !state.loading,
                    onClick = onRefresh,
                )
            }
        },
    ) {
        clashStatus(state.loading, state.error, state.unsupported)

        item {
            Text(
                text = stringResource(Res.string.clash_smart_groups),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (state.groups.isEmpty() && !state.loading) {
            item {
                if (state.message.isNotBlank()) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    EmptyClashList()
                }
            }
        }

        for (group in state.groups) {
            val refreshKey = ClashSmartModel.refreshGroupKey(group.name)
            val clearKey = ClashSmartModel.clearFixedKey(group.name)
            clashCard(key = "smart-group-${group.name}") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        val selectionText = if (group.fixed.isNotEmpty()) {
                            stringResource(Res.string.clash_smart_fixed_node, group.fixed)
                        } else {
                            group.selected
                        }
                        if (selectionText.isNotBlank()) {
                            Text(
                                text = selectionText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (group.fixed.isNotEmpty()) {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.undo),
                            contentDescription = stringResource(Res.string.smart_clear_fixed),
                            enabled = state.pending[clearKey] != true && state.unavailable[clearKey] != true,
                            onClick = { onClearFixed(group.name) },
                        )
                    }
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.cached),
                        contentDescription = stringResource(Res.string.clash_smart_refresh_weights),
                        enabled = state.pending[refreshKey] != true && state.unavailable[refreshKey] != true,
                        onClick = { onRefreshGroupWeights(group.name) },
                    )
                }

                val optionsSummary = buildSmartGroupOptionsSummary(group)
                if (optionsSummary.isNotEmpty()) {
                    Text(
                        text = optionsSummary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                when {
                    state.unavailable[refreshKey] == true -> Text(
                        text = stringResource(Res.string.clash_unsupported),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    !state.actionErrors[refreshKey].isNullOrEmpty() -> Text(
                        text = state.actionErrors.getValue(refreshKey),
                        color = MaterialTheme.colorScheme.error,
                    )

                    group.error.isNotEmpty() -> Text(
                        text = group.error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (group.ranks.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.clash_smart_no_weights),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (rank in group.ranks) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = rank.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val detail = listOf(
                                    smartRankLabel(rank.rank),
                                    formatSmartWeight(rank.weight),
                                ).filter { it.isNotEmpty() }.joinToString(" · ")
                                Text(
                                    text = detail,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        clashCard(key = "smart-cache") {
            Text(stringResource(Res.string.clash_smart_cache), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { showFlushAllConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !flushAllBusy && !flushAllUnavailable,
            ) {
                Text(stringResource(Res.string.clash_smart_flush_all))
            }
            if (flushAllUnavailable) {
                Text(
                    text = stringResource(Res.string.clash_unsupported),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = configName,
                onValueChange = { configName = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !flushConfigBusy && !flushConfigUnavailable,
                label = { Text(stringResource(Res.string.clash_smart_config_name)) },
                isError = configName.isNotEmpty() && !configValid,
                supportingText = if (configName.isNotEmpty() && !configValid) {
                    { Text(stringResource(Res.string.clash_smart_config_name_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Button(
                onClick = { showFlushConfigConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = configValid && !flushConfigBusy && !flushConfigUnavailable,
            ) {
                Text(stringResource(Res.string.clash_smart_flush_config))
            }
            if (flushConfigUnavailable) {
                Text(
                    text = stringResource(Res.string.clash_unsupported),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        clashCard(key = "smart-lgbm") {
            Text(stringResource(Res.string.clash_smart_lgbm), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onUpgradeLgbm,
                modifier = Modifier.fillMaxWidth(),
                enabled = !upgradeLgbmBusy && !upgradeLgbmUnavailable,
            ) {
                Text(stringResource(Res.string.clash_smart_upgrade_lgbm))
            }
            if (upgradeLgbmUnavailable) {
                Text(
                    text = stringResource(Res.string.clash_unsupported),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showFlushAllConfirm) {
        AlertDialog(
            onDismissRequest = { showFlushAllConfirm = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showFlushAllConfirm = false
                    onFlushAllCache()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showFlushAllConfirm = false
                }
            },
            title = { Text(stringResource(Res.string.clash_smart_flush_all)) },
            text = { Text(stringResource(Res.string.clash_smart_flush_all_confirm)) },
        )
    }

    if (showFlushConfigConfirm) {
        val normalized = remember(configName) { ClashSmartModel.normalizeConfigName(configName) }
        AlertDialog(
            onDismissRequest = { showFlushConfigConfirm = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showFlushConfigConfirm = false
                    onFlushConfigCache(normalized)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showFlushConfigConfirm = false
                }
            },
            title = { Text(stringResource(Res.string.clash_smart_flush_config)) },
            text = { Text(stringResource(Res.string.clash_smart_flush_config_confirm, normalized)) },
        )
    }
}

@Composable
private fun buildSmartGroupOptionsSummary(group: ClashSmartGroupInfo): String {
    val parts = buildList {
        if (group.useLightGBM == true) {
            add(stringResource(Res.string.clash_smart_lightgbm))
        }
        if (group.collectData == true) {
            add(stringResource(Res.string.clash_smart_collect_data))
        }
        if (group.preferASN == true) {
            add(stringResource(Res.string.clash_smart_prefer_asn))
        }
        group.sampleRate?.let { rate ->
            val formatted = formatSmartWeight(rate)
            if (formatted.isNotEmpty()) {
                add(stringResource(Res.string.clash_smart_sample_rate, formatted))
            }
        }
        group.tolerance?.let { tolerance ->
            add(stringResource(Res.string.clash_smart_tolerance, tolerance))
        }
        if (group.emptyFallback.isNotEmpty()) {
            add(stringResource(Res.string.clash_smart_empty_fallback, group.emptyFallback))
        }
        if (group.expectedStatus.isNotEmpty()) {
            add(stringResource(Res.string.clash_smart_expected_status, group.expectedStatus))
        }
        if (group.policyPriority.isNotEmpty()) {
            add(stringResource(Res.string.clash_smart_policy_priority, group.policyPriority))
        }
    }
    return parts.joinToString(" · ")
}
