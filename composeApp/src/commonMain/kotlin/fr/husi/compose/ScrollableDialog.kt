package fr.husi.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

@Composable
fun ScrollableDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    textPadding: PaddingValues = PaddingValues(),
    text: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = {
            val density = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val maxTextHeight =
                with(density) { windowInfo.containerSize.height.toDp() }.takeIf { it > 0.dp }
                    ?.times(0.8f)
                    ?: 480.dp
            VerticalScrollColumn(
                modifier = Modifier.heightIn(max = maxTextHeight),
                contentPadding = textPadding,
            ) {
                text()
            }
        },
    )
}
