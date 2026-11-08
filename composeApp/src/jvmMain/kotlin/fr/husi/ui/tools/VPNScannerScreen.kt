package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun VPNScannerScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
)
