package fr.husi.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import fr.husi.bg.ServiceState

private fun pathNodes(data: String): List<PathNode> =
    PathParser().parsePathString(data).toNodes()

// ic_service_idle strike-through path morph endpoints
private val STRIKE_PATH_VISIBLE =
    pathNodes("M 19.73 22 L 21 20.73 L 3.27 3 L 2 4.27 Z")
private val STRIKE_PATH_GONE =
    pathNodes("M 2 4.27 L 3.27 3 L 3.27 3 L 2 4.27 Z")
private val STRIKE_CLIP_VISIBLE =
    pathNodes("M 0 0 L 24 0 L 24 24 L 0 24 L 0 0 Z M 4.54 1.73 L 3.27 3 L 21 20.73 L 22.27 19.46 Z")
private val STRIKE_CLIP_GONE =
    pathNodes("M 0 0 L 24 0 L 24 24 L 0 24 L 0 0 Z M 4.54 1.73 L 3.27 3 L 3.27 3 L 4.54 1.73 Z")
private val PLANE_ICON =
    pathNodes("M17.68,9l-1.59,7L12.7,14.89l5-5.93M10,10.08l-3.57,3L5,12.55l5-2.47M21.25,2.28L0,12.8l6.83,2.57,9.76-8.21L9.26,15.89l8.29,2.67,3.7-16.27h0ZM 9.45 17.56 L 9.46 22 L 12.09 18.41 L 9.45 17.56 L 9.45 17.56 Z")

// ic_service_stopping fold-line morph endpoints
private val FOLDS_HIDDEN =
    pathNodes("M 15.5 13.28 L 15.5 13.28 L 15.5 13.28 L 15.5 13.28 M 7.14 11.9 L 7.14 11.9 L 7.14 11.9 L 7.14 11.9 M 21.25 2.28 L 0 12.8 L 6.83 15.37 L 16.59 7.16 L 9.26 15.89 L 17.55 18.56 L 21.25 2.29 L 21.25 2.29 Z M 9.45 17.56 L 9.46 22 L 12.09 18.41 L 9.45 17.56 L 9.45 17.56 Z")
private val FOLDS_VISIBLE =
    pathNodes("M 17.68 9 L 16.09 16 L 12.7 14.89 L 17.7 8.96 M 10 10.08 L 6.43 13.08 L 5 12.55 L 10 10.08 M 21.25 2.28 L 0 12.8 L 6.83 15.37 L 16.59 7.16 L 9.26 15.89 L 17.55 18.56 L 21.25 2.29 L 21.25 2.29 Z M 9.45 17.56 L 9.46 22 L 12.09 18.41 L 9.45 17.56 L 9.45 17.56 Z")

private fun lerpNodes(from: List<PathNode>, to: List<PathNode>, t: Float): List<PathNode> =
    from.zip(to) { a, b ->
        when (a) {
            is PathNode.MoveTo if b is PathNode.MoveTo -> PathNode.MoveTo(
                a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
            )

            is PathNode.LineTo if b is PathNode.LineTo -> PathNode.LineTo(
                a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
            )

            is PathNode.Close if b is PathNode.Close -> PathNode.Close
            else -> a
        }
    }

private fun idleVector(
    strikePath: List<PathNode>,
    strikeClip: List<PathNode>,
) = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = strikePath, fill = SolidColor(Color.White),
).addGroup(
    clipPathData = strikeClip,
).addPath(
    pathData = PLANE_ICON, fill = SolidColor(Color.White),
).clearGroup().build()

private fun busyVector(path: List<PathNode>) = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = path, fill = SolidColor(Color.White),
).build()

@Composable
actual fun AnimatedServiceIcon(state: ServiceState, contentDescription: String) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    val vector = when (state) {
        ServiceState.Connecting -> idleVector(
            lerpNodes(STRIKE_PATH_VISIBLE, STRIKE_PATH_GONE, progress.value),
            lerpNodes(STRIKE_CLIP_VISIBLE, STRIKE_CLIP_GONE, progress.value),
        )
        ServiceState.Stopping -> busyVector(
            lerpNodes(FOLDS_HIDDEN, FOLDS_VISIBLE, progress.value),
        )
        else -> idleVector(
            lerpNodes(STRIKE_PATH_GONE, STRIKE_PATH_VISIBLE, progress.value),
            lerpNodes(STRIKE_CLIP_GONE, STRIKE_CLIP_VISIBLE, progress.value),
        )
    }
    Icon(rememberVectorPainter(vector), contentDescription)
}
