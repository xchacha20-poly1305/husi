package fr.husi.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

@Composable
internal actual fun ProcessIcon(icon: Any?, contentDescription: String?, modifier: Modifier) {
    if (icon is ImageBitmap) {
        Image(bitmap = icon, contentDescription = contentDescription, modifier = modifier)
    }
}
