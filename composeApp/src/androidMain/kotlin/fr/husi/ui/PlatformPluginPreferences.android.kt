package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import fr.husi.compose.IconMaskColors
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.MaskedIcon
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.preferenceGroup
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.copyright
import fr.husi.resources.custom_plugin_prefix
import fr.husi.resources.custom_plugin_prefix_summary
import org.jetbrains.compose.resources.stringResource

internal actual fun LazyListScope.platformPluginPreferences(
    isExpert: Boolean,
    needRestart: () -> Unit,
) {
    if (!isExpert) return
    preferenceGroup {
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
