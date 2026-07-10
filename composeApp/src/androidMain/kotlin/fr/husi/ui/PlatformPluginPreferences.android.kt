package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.preferenceGroup
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.copyright
import fr.husi.resources.custom_plugin_prefix
import fr.husi.resources.custom_plugin_prefix_summary
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

internal actual fun LazyListScope.platformPluginPreferences(
    needRestart: () -> Unit,
) {
    if (!isExpert) return
    preferenceGroup {
        val value by DataStore.configurationStore
            .stringFlow(Key.CUSTOM_PLUGIN_PREFIX, "")
            .collectAsStateWithLifecycle("")
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.customPluginPrefix = it
                needRestart()
            },
            title = { Text(stringResource(Res.string.custom_plugin_prefix)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.copyright,
                    color = PreferenceMaskColors.IconCoral,
                )
            },
            summary = { Text(stringResource(Res.string.custom_plugin_prefix_summary)) },
            valueToText = { it },
        )
    }
}
