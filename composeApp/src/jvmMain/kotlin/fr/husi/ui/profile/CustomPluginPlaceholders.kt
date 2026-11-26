package fr.husi.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Text
import fr.husi.compose.setPlainText
import fr.husi.fmt.plugin.PLACEHOLDER_CACHE_DIR
import fr.husi.fmt.plugin.PLACEHOLDER_CA_CONTENT
import fr.husi.fmt.plugin.PLACEHOLDER_CA_CONTENT_JSON
import fr.husi.fmt.plugin.PLACEHOLDER_CA_PATH
import fr.husi.fmt.plugin.PLACEHOLDER_CONFIG
import fr.husi.fmt.plugin.PLACEHOLDER_FINAL_ADDR
import fr.husi.fmt.plugin.PLACEHOLDER_FINAL_HOST
import fr.husi.fmt.plugin.PLACEHOLDER_FINAL_PORT
import fr.husi.fmt.plugin.PLACEHOLDER_LISTEN_ADDR
import fr.husi.fmt.plugin.PLACEHOLDER_LISTEN_HOST
import fr.husi.fmt.plugin.PLACEHOLDER_LISTEN_PORT
import fr.husi.fmt.plugin.PLACEHOLDER_LOG_LEVEL
import fr.husi.fmt.plugin.PLACEHOLDER_PROTECT_PATH
import fr.husi.fmt.plugin.PLACEHOLDER_SERVER_HOST
import fr.husi.fmt.plugin.PLACEHOLDER_SERVER_PORT
import fr.husi.resources.Res
import fr.husi.resources.action_copy
import fr.husi.resources.check
import fr.husi.resources.content_copy
import fr.husi.resources.custom_plugin_placeholder_ca_content
import fr.husi.resources.custom_plugin_placeholder_ca_content_json
import fr.husi.resources.custom_plugin_placeholder_ca_path
import fr.husi.resources.custom_plugin_placeholder_cache_dir
import fr.husi.resources.custom_plugin_placeholder_config
import fr.husi.resources.custom_plugin_placeholder_final_addr
import fr.husi.resources.custom_plugin_placeholder_final_host
import fr.husi.resources.custom_plugin_placeholder_final_port
import fr.husi.resources.custom_plugin_placeholder_listen_addr
import fr.husi.resources.custom_plugin_placeholder_listen_host
import fr.husi.resources.custom_plugin_placeholder_listen_port
import fr.husi.resources.custom_plugin_placeholder_log_level
import fr.husi.resources.custom_plugin_placeholder_protect_path
import fr.husi.resources.custom_plugin_placeholder_reference
import fr.husi.resources.custom_plugin_placeholder_server_host
import fr.husi.resources.custom_plugin_placeholder_server_port
import fr.husi.resources.ok
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private data class CustomPluginPlaceholderDoc(
    val token: String,
    val description: StringResource,
    val availableInConfig: Boolean = true,
)

private val ALL_CUSTOM_PLUGIN_PLACEHOLDERS = listOf(
    CustomPluginPlaceholderDoc(PLACEHOLDER_LISTEN_HOST, Res.string.custom_plugin_placeholder_listen_host),
    CustomPluginPlaceholderDoc(PLACEHOLDER_LISTEN_PORT, Res.string.custom_plugin_placeholder_listen_port),
    CustomPluginPlaceholderDoc(PLACEHOLDER_LISTEN_ADDR, Res.string.custom_plugin_placeholder_listen_addr),
    CustomPluginPlaceholderDoc(PLACEHOLDER_FINAL_HOST, Res.string.custom_plugin_placeholder_final_host),
    CustomPluginPlaceholderDoc(PLACEHOLDER_FINAL_PORT, Res.string.custom_plugin_placeholder_final_port),
    CustomPluginPlaceholderDoc(PLACEHOLDER_FINAL_ADDR, Res.string.custom_plugin_placeholder_final_addr),
    CustomPluginPlaceholderDoc(PLACEHOLDER_SERVER_HOST, Res.string.custom_plugin_placeholder_server_host),
    CustomPluginPlaceholderDoc(PLACEHOLDER_SERVER_PORT, Res.string.custom_plugin_placeholder_server_port),
    CustomPluginPlaceholderDoc(
        PLACEHOLDER_CONFIG,
        Res.string.custom_plugin_placeholder_config,
        availableInConfig = false,
    ),
    CustomPluginPlaceholderDoc(PLACEHOLDER_PROTECT_PATH, Res.string.custom_plugin_placeholder_protect_path),
    CustomPluginPlaceholderDoc(PLACEHOLDER_CA_PATH, Res.string.custom_plugin_placeholder_ca_path),
    CustomPluginPlaceholderDoc(PLACEHOLDER_CA_CONTENT, Res.string.custom_plugin_placeholder_ca_content),
    CustomPluginPlaceholderDoc(
        PLACEHOLDER_CA_CONTENT_JSON,
        Res.string.custom_plugin_placeholder_ca_content_json,
    ),
    CustomPluginPlaceholderDoc(PLACEHOLDER_LOG_LEVEL, Res.string.custom_plugin_placeholder_log_level),
    CustomPluginPlaceholderDoc(PLACEHOLDER_CACHE_DIR, Res.string.custom_plugin_placeholder_cache_dir),
)

@Composable
internal fun CustomPluginPlaceholderReferenceDialog(
    includeConfigPlaceholder: Boolean,
    onDismiss: () -> Unit,
    onInsert: ((String) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copiedToken by remember { mutableStateOf<String?>(null) }
    val placeholders = remember(includeConfigPlaceholder) {
        if (includeConfigPlaceholder) {
            ALL_CUSTOM_PLUGIN_PLACEHOLDERS
        } else {
            ALL_CUSTOM_PLUGIN_PLACEHOLDERS.filter { it.availableInConfig }
        }
    }

    ScrollableDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(stringResource(Res.string.ok), onClick = onDismiss)
        },
        title = { Text(stringResource(Res.string.custom_plugin_placeholder_reference)) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in placeholders) {
                val copyAction = {
                    scope.launch {
                        clipboard.setPlainText(item.token)
                        copiedToken = item.token
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (onInsert != null) {
                                onInsert(item.token)
                            } else {
                                copyAction()
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.token,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(item.description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (onInsert == null) {
                        SimpleIconButton(
                            imageVector = vectorResource(
                                if (copiedToken == item.token) {
                                    Res.drawable.check
                                } else {
                                    Res.drawable.content_copy
                                },
                            ),
                            contentDescription = stringResource(Res.string.action_copy),
                            onClick = { copyAction() },
                        )
                    }
                }
            }
        }
    }
}
