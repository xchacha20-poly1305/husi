package fr.husi.compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize

@Composable
actual fun WindowedDialog(
    onDismissRequest: () -> Unit,
    title: String,
    windowSize: DpSize,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    InWindowDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        buttons = buttons,
        content = content,
    )
}
