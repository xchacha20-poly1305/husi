package fr.husi.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.onClick
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent

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

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.platformContextClickable(onClick: (Offset) -> Unit): Modifier = onPointerEvent(
    eventType = PointerEventType.Press,
) { event ->
    if (event.buttons.isSecondaryPressed) {
        event.changes.firstOrNull()?.let { onClick(it.position) }
    }
}
