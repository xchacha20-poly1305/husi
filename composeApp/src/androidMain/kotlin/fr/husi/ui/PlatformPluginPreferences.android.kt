package fr.husi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.ProfilePreferenceIcon
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.copyright
import fr.husi.resources.custom_plugin_prefix
import fr.husi.resources.custom_plugin_prefix_summary
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun PlatformPluginPreferences(
    isExpert: Boolean,
    needRestart: () -> Unit,
) {
    if (!isExpert) return
    val value by DataStore.configurationStore
        .stringFlow(Key.CUSTOM_PLUGIN_PREFIX, "")
        .collectAsStateWithLifecycle("")
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.customPluginPrefix = it
                needRestart()
            },
            title = { Text(stringResource(Res.string.custom_plugin_prefix)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(
                    Res.drawable.copyright,
                    color = PreferenceMaskColors.IconCoral,
                )
            },
            summary = { Text(stringResource(Res.string.custom_plugin_prefix_summary)) },
            valueToText = { it },
        )
    }
}
