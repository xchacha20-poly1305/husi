package fr.husi

import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastCoerceIn
import dev.nucleusframework.composenativetray.utils.IconRenderProperties
import fr.husi.ktx.Logs
import java.awt.GraphicsEnvironment
import kotlin.math.ceil

const val TRAY_ICON_DP = 24
const val MENU_ICON_DP = 16

private const val MIN_DISPLAY_SCALE = 1f
private const val MAX_DISPLAY_SCALE = 4f

/**
 * Upstream calculate scaling by **system** instead of scale. This reads current scale to fix it.
 */
fun trayIconRenderProperties(sizeDp: Int): IconRenderProperties {
    val scale = displayScaleFactor()
    val sizePixels = ceil(sizeDp * scale).toInt()
    return IconRenderProperties.withoutScalingAndAliasing(
        sceneWidth = sizePixels,
        sceneHeight = sizePixels,
        density = Density(scale),
    )
}

private fun displayScaleFactor(): Float {
    if (GraphicsEnvironment.isHeadless()) return MIN_DISPLAY_SCALE
    val scale = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .defaultTransform
            .scaleX
            .toFloat()
    }.getOrElse { error ->
        Logs.w("read the primary screen scale for tray icons", error)
        MIN_DISPLAY_SCALE
    }
    return scale.fastCoerceIn(MIN_DISPLAY_SCALE, MAX_DISPLAY_SCALE)
}
