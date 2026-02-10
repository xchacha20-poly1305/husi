package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import fr.husi.compose.MultilineTextField
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.listByLineOrComma
import fr.husi.resources.Res
import fr.husi.resources.legend_toggle
import fr.husi.resources.process
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal actual fun LazyListScope.appSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
) {
    item("process") {
        val content = packages.joinToString("\n")
        TextFieldPreference(
            value = content,
            onValueChange = { text ->
                onSelectApps(text.listByLineOrComma().toSet())
            },
            title = { Text(stringResource(Res.string.process)) },
            icon = { Icon(vectorResource(Res.drawable.legend_toggle), null) },
            summary = { Text(contentOrUnset(content)) },
            textToValue = { it },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
}

internal actual fun LazyListScope.proxyAppsPreferences(
    openAppManager: () -> Unit,
) {
}
