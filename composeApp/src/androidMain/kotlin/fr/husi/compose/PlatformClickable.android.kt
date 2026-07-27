package fr.husi.compose

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.platformCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = combinedClickable(onClick = onClick, onLongClick = onLongClick)

actual fun Modifier.platformContextClickable(onClick: (Offset) -> Unit): Modifier = pointerInput(onClick) {
    detectTapGestures(onLongPress = onClick)
}
