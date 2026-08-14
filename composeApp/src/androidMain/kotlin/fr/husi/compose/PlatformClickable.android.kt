package fr.husi.compose

import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

actual fun Modifier.platformCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = combinedClickable(onClick = onClick, onLongClick = onLongClick)

actual fun Modifier.platformContextClickable(onClick: (Offset) -> Unit): Modifier = pointerInput(onClick) {
    detectTapGestures(onLongPress = onClick)
}

actual fun Modifier.platformSelectable(
    selected: Boolean,
    enabled: Boolean,
    role: Role,
    interactionSource: MutableInteractionSource,
    indication: Indication,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier = this
    .semantics { this.selected = selected }
    .combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        role = role,
        onClick = onClick,
        onLongClick = onLongClick,
    )
