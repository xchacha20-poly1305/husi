package fr.husi.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
actual fun PlatformMenuIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
): (@Composable () -> Unit)? = {
    SimpleIconButton(
        imageVector = imageVector,
        contentDescription = contentDescription,
        onClick = onClick,
    )
}