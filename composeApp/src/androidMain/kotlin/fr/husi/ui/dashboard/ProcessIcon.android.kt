package fr.husi.ui.dashboard

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
internal actual fun ProcessIcon(icon: Any?, contentDescription: String?, modifier: Modifier) {
    if (icon is Drawable) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}
