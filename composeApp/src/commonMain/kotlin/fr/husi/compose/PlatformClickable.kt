package fr.husi.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

expect fun Modifier.platformCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier

expect fun Modifier.platformContextClickable(onClick: (Offset) -> Unit): Modifier
