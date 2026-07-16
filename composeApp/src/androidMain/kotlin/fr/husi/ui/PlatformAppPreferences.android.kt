package fr.husi.ui

import androidx.compose.runtime.Composable
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.apps
import fr.husi.resources.apps_message
import fr.husi.resources.legend_toggle
import fr.husi.resources.not_set
import me.zhanghai.compose.preference.Preference
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AppSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
) {
    Preference(
        title = { Text(stringResource(Res.string.apps)) },
        icon = {
            MaskedIcon(
                resource = Res.drawable.legend_toggle,
                color = IconMaskColors.IconLavender,
            )
        },
        summary = {
            val text = when (val size = packages.size) {
                0 -> stringResource(Res.string.not_set)
                in 1..5 -> packages.joinToString("\n")
                else -> pluralStringResource(Res.plurals.apps_message, size, size)
            }
            Text(text)
        },
        onClick = {
            onSelectApps(packages)
        },
    )
}
