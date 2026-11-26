package fr.husi.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.result.ResultEffect
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.Preference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextButton
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.fmt.plugin.CustomPluginBean
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.code
import fr.husi.resources.custom_plugin
import fr.husi.resources.custom_plugin_command_args
import fr.husi.resources.custom_plugin_config_content
import fr.husi.resources.custom_plugin_config_file_name
import fr.husi.resources.custom_plugin_environment
import fr.husi.resources.custom_plugin_environment_summary_empty
import fr.husi.resources.custom_plugin_launch_config
import fr.husi.resources.custom_plugin_placeholder_reference
import fr.husi.resources.custom_plugin_server_address_summary
import fr.husi.resources.custom_plugin_server_mapping
import fr.husi.resources.custom_plugin_support_protect
import fr.husi.resources.custom_plugin_support_protect_summary
import fr.husi.resources.description
import fr.husi.resources.directions_boat
import fr.husi.resources.emoji_symbols
import fr.husi.resources.lines
import fr.husi.resources.not_set
import fr.husi.resources.ok
import fr.husi.resources.profile_name
import fr.husi.resources.question_mark
import fr.husi.resources.router
import fr.husi.resources.security
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.terminal
import fr.husi.resources.view_list
import fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random

@Composable
fun CustomPluginSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: CustomPluginSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        CustomPluginSettingsViewModel()
    }

    val resultKeyNumber = rememberSaveable {
        viewModel.editingId.takeIf { it >= 0L } ?: Random.nextLong()
    }
    val configResultKey = "custom-plugin-config-result-$resultKeyNumber"
    ResultEffect<String>(resultKey = configResultKey) { result ->
        viewModel.setConfig(result)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.custom_plugin,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        val state = uiState as CustomPluginUiState
        customPluginSettings(
            uiState = state,
            viewModel = viewModel,
            onOpenConfigContentEditor = {
                onOpenConfigEditor(
                    NavRoutes.ConfigEditor(
                        initialText = viewModel.uiState.value.config,
                        resultKey = configResultKey,
                        schema = null,
                        showPluginPlaceholders = true,
                    ),
                )
            },
        )
    }
}

private fun LazyListScope.customPluginSettings(
    uiState: CustomPluginUiState,
    viewModel: CustomPluginSettingsViewModel,
    onOpenConfigContentEditor: () -> Unit,
) {
    preferenceGroup(key = "general") {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = viewModel::setName,
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
        CustomPluginExecutableRow(
            pluginId = uiState.pluginId,
            executablePath = uiState.executablePath,
            onPluginIdChange = viewModel::setPluginId,
            onExecutablePathChange = viewModel::setExecutablePath,
        )
        SwitchPreference(
            value = uiState.supportProtect,
            onValueChange = viewModel::setSupportProtect,
            title = { Text(stringResource(Res.string.custom_plugin_support_protect)) },
            icon = {
                MaskedIcon(
                    Res.drawable.security,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(stringResource(Res.string.custom_plugin_support_protect_summary)) },
        )
    }

    item("category_launch_config") {
        PreferenceCategory(text = { Text(stringResource(Res.string.custom_plugin_launch_config)) })
    }
    preferenceGroup(key = "launch_config") {
        MultilineTextWithPlaceholdersPreference(
            value = uiState.commandArgs,
            onValueChange = viewModel::setCommandArgs,
            title = stringResource(Res.string.custom_plugin_command_args),
            icon = {
                MaskedIcon(
                    Res.drawable.terminal,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(uiState.commandArgs)) },
        )
        TextFieldPreference(
            value = uiState.configFileName,
            onValueChange = viewModel::setConfigFileName,
            title = { Text(stringResource(Res.string.custom_plugin_config_file_name)) },
            textToValue = { it.trim().ifBlank { CustomPluginBean.DEFAULT_CONFIG_FILE_NAME } },
            icon = {
                MaskedIcon(
                    Res.drawable.description,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(uiState.configFileName)) },
            valueToText = { it },
        )
        Preference(
            title = { Text(stringResource(Res.string.custom_plugin_config_content)) },
            icon = {
                MaskedIcon(
                    Res.drawable.code,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = {
                val text = if (uiState.config.isBlank()) {
                    stringResource(Res.string.not_set)
                } else {
                    val count = uiState.config.count { it == '\n' } + 1
                    pluralStringResource(Res.plurals.lines, count, count)
                }
                Text(text)
            },
            onClick = onOpenConfigContentEditor,
        )
        MultilineTextWithPlaceholdersPreference(
            value = uiState.environment,
            onValueChange = viewModel::setEnvironment,
            title = stringResource(Res.string.custom_plugin_environment),
            icon = {
                MaskedIcon(
                    Res.drawable.view_list,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = {
                val text = if (uiState.environment.isBlank()) {
                    stringResource(Res.string.custom_plugin_environment_summary_empty)
                } else {
                    val count = uiState.environment.lineSequence().count { it.isNotBlank() }
                    pluralStringResource(Res.plurals.lines, count, count)
                }
                Text(text)
            },
        )
    }

    item("category_server_mapping") {
        PreferenceCategory(text = { Text(stringResource(Res.string.custom_plugin_server_mapping)) })
    }
    preferenceGroup(key = "server_mapping") {
        TextFieldPreference(
            value = uiState.serverAddress,
            onValueChange = { viewModel.setServerAddress(it.trim()) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it.trim() },
            icon = {
                MaskedIcon(
                    Res.drawable.router,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = {
                Text(
                    uiState.serverAddress.ifBlank {
                        stringResource(Res.string.custom_plugin_server_address_summary)
                    },
                )
            },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.serverPort,
            onValueChange = viewModel::setServerPort,
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull()?.takeIf { port -> port in 1..65535 } ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.serverPort)) },
            valueToText = { if (it in 1..65535) it.toString() else "" },
            textField = { state, onOk ->
                UIntegerTextField(state, onOk)
            },
        )
    }
}

@Composable
private fun MultilineTextWithPlaceholdersPreference(
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    icon: @Composable () -> Unit,
    summary: @Composable () -> Unit,
) {
    var openDialog by rememberSaveable { mutableStateOf(false) }

    Preference(
        title = { Text(title) },
        icon = icon,
        summary = summary,
        onClick = { openDialog = true },
    )

    if (openDialog) {
        MultilineTextWithPlaceholdersDialog(
            title = title,
            initialText = value,
            onDismiss = { openDialog = false },
            onConfirm = { newText ->
                onValueChange(newText)
                openDialog = false
            },
        )
    }
}

@Composable
private fun MultilineTextWithPlaceholdersDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val textFieldState = rememberTextFieldState(initialText = initialText)
    var showPlaceholders by rememberSaveable { mutableStateOf(false) }
    val onOk = { onConfirm(textFieldState.text.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(stringResource(Res.string.ok), onClick = onOk)
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel), onClick = onDismiss)
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                )
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.question_mark),
                    contentDescription = stringResource(Res.string.custom_plugin_placeholder_reference),
                    onClick = { showPlaceholders = true },
                )
            }
        },
        text = {
            MultilineTextField(
                state = textFieldState,
                onOk = onOk,
            )
        },
    )

    if (showPlaceholders) {
        CustomPluginPlaceholderReferenceDialog(
            includeConfigPlaceholder = true,
            onDismiss = { showPlaceholders = false },
        )
    }
}
