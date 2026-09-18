package fr.husi.compose.material3

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast

@Composable
fun ScrollableDropdownMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val safeDrawing = WindowInsets.safeDrawing
    val maxHeight = with(density) {
        val insets = safeDrawing.getTop(density) + safeDrawing.getBottom(density)
        (windowHeight - insets).toDp() - 48.dp
    }.coerceAtLeast(96.dp)

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}
