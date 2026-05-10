package fr.husi.compose

import androidx.compose.ui.Modifier

expect fun Modifier.platformCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier
