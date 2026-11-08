package fr.husi.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.ScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import io.github.oikvpqya.compose.fastscroller.TrackStyle

/** Compile Material styling with the app: fastscroller-material3 0.3.2 embeds
 *  incompatible MaterialTheme references on Wasm.
 * @see <a href="Upstream issue">https://github.com/oikvpqya/fastscroller-compose-multiplatform/issues/20</a>
 */
object HusiScrollbarDefaults {
    @Composable
    fun style(thickness: Dp = 12.dp): ScrollbarStyle {
        val colorScheme = MaterialTheme.colorScheme
        val thumbColor = colorScheme.primary
        val trackColor = colorScheme.surfaceColorAtElevation(8.dp)
        val shape = RoundedCornerShape(4.dp)
        return ScrollbarStyle(
            minimalHeight = 52.dp,
            thickness = thickness,
            hoverDurationMillis = 300,
            thumbStyle = ThumbStyle(
                shape = shape,
                unhoverColor = thumbColor,
                hoverColor = thumbColor,
            ),
            trackStyle = TrackStyle(
                shape = shape,
                unhoverColor = trackColor,
                hoverColor = trackColor,
            ),
        )
    }
}
