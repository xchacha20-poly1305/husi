package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun AppManagerScreen(
    onBackPress: () -> Unit,
    modifier: Modifier,
) {
    error("AppManagerScreen is not supported on this platform")
}
