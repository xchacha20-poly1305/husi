package fr.husi.compose

import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role

expect fun Modifier.platformCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier

expect fun Modifier.platformContextClickable(onClick: (Offset) -> Unit): Modifier

expect fun Modifier.platformSelectable(
    selected: Boolean,
    enabled: Boolean,
    role: Role,
    interactionSource: MutableInteractionSource,
    indication: Indication,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier
