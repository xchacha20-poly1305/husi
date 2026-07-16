package fr.husi.ui

import androidx.compose.runtime.Composable
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.material3.Text
import fr.husi.compose.MultilineTextField
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.listByLineOrComma
import fr.husi.resources.Res
import fr.husi.resources.legend_toggle
import fr.husi.resources.process
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AppSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
) {
    val content = packages.joinToString("\n")
    TextFieldPreference(
        value = content,
        onValueChange = { text ->
            onSelectApps(text.listByLineOrComma().toSet())
        },
        title = { Text(stringResource(Res.string.process)) },
        icon = {
            MaskedIcon(
                resource = Res.drawable.legend_toggle,
                color = IconMaskColors.IconLavender,
            )
        },
        summary = { Text(contentOrUnset(content)) },
        textToValue = { it },
        valueToText = { it },
        textField = { value, onValueChange, onOk ->
            MultilineTextField(value, onValueChange, onOk)
        },
    )
}
