package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun AppManagerScreen(
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
)
