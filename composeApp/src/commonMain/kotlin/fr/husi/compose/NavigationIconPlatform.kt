package fr.husi.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
expect fun PlatformMenuIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: ()-> Unit,
)