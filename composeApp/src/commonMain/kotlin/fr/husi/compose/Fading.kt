package fr.husi.compose

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost

fun Modifier.fadingEdge(
    scrollableState: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    length: Dp = 64.dp,
    fadeStart: Boolean = false,
    fadeEnd: Boolean = true,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()

        val isHorizontal = orientation == Orientation.Horizontal
        val totalLength = if (isHorizontal) size.width else size.height
        if (totalLength <= 0f) return@drawWithContent

        val limitFactor = if (fadeStart && fadeEnd) {
            2f
        } else {
            1f
        }
        val fadePx = length.toPx().fastCoerceAtMost(totalLength / limitFactor)

        if (fadeStart && scrollableState.canScrollBackward) {
            drawFadeRect(
                isHorizontal = isHorizontal,
                isStart = true,
                fadePx = fadePx,
                totalLength = totalLength,
            )
        }

        if (fadeEnd && scrollableState.canScrollForward) {
            drawFadeRect(
                isHorizontal = isHorizontal,
                isStart = false,
                fadePx = fadePx,
                totalLength = totalLength,
            )
        }
    }

private fun ContentDrawScope.drawFadeRect(
    isHorizontal: Boolean,
    isStart: Boolean,
    fadePx: Float,
    totalLength: Float,
) {
    val colors = if (isStart) {
        listOf(Color.Transparent, Color.Black)
    } else {
        listOf(Color.Black, Color.Transparent)
    }
    val startPx = if (isStart) {
        0f
    } else {
        totalLength - fadePx
    }
    val endPx = if (isStart) {
        fadePx
    } else {
        totalLength
    }

    val brush = if (isHorizontal) {
        Brush.horizontalGradient(colors, startX = startPx, endX = endPx)
    } else {
        Brush.verticalGradient(colors, startY = startPx, endY = endPx)
    }

    val topLeft = if (isHorizontal) {
        Offset(x = startPx, y = 0f)
    } else {
        Offset(x = 0f, y = startPx)
    }
    val rectSize = if (isHorizontal) {
        Size(width = fadePx, height = size.height)
    } else {
        Size(
            width = size.width,
            height = fadePx,
        )
    }

    drawRect(
        brush = brush,
        topLeft = topLeft,
        size = rectSize,
        blendMode = BlendMode.DstIn,
    )
}