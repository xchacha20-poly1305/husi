package fr.husi.compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize

/**
 * A dialog for content that outgrows an in-window dialog: long forms or tool UIs.
 *
 * Desktop shows it as a separate, resizable, modeless OS window, so it neither blocks nor is
 * hidden by the main window. Android shows it as an in-window dialog.
 *
 * [content] is laid out in a column whose height is bounded; scrolling is the content's job.
 * [windowSize] is the initial desktop window size and is ignored on Android.
 */
@Composable
expect fun WindowedDialog(
    onDismissRequest: () -> Unit,
    title: String,
    windowSize: DpSize,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
)
