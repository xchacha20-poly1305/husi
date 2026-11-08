package fr.husi.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.onClick
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.platformCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = this
    .clickable(onClick = onClick)
    .onClick(
        matcher = PointerMatcher.mouse(PointerButton.Secondary),
        onClick = onLongClick,
    )

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.platformSelectable(
    selected: Boolean,
    enabled: Boolean,
    role: Role,
    interactionSource: MutableInteractionSource,
    indication: Indication,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier {
    val selectable = selectable(
        selected = selected,
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
    if (onLongClick == null || !enabled) return selectable
    return selectable.onClick(
        matcher = PointerMatcher.mouse(PointerButton.Secondary),
        onClick = onLongClick,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.platformContextClickable(onClick: (Offset) -> Unit): Modifier = onPointerEvent(
    eventType = PointerEventType.Press,
) { event ->
    if (event.buttons.isSecondaryPressed) {
        event.changes.firstOrNull()?.let { onClick(it.position) }
    }
}
