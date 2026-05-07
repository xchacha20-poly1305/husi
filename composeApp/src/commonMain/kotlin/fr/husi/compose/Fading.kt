package fr.husi.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost

/** Feather the bottom edge while there is more content below the viewport. */
fun Modifier.fadingBottomEdge(
    listState: LazyListState,
    height: Dp = 64.dp,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (listState.canScrollForward) {
            val fadeHeightPx = height.toPx().fastCoerceAtMost(size.height)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - fadeHeightPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
