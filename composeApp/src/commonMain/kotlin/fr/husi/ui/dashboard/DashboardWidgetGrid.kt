package fr.husi.ui.dashboard

import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.lookaheadScopeCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.zIndex
import fr.husi.platform.PlatformInfo
import kotlin.math.roundToInt

// A card is Small (four columns) or Wide (eight), so the column count is a multiple of four and
// fixes how many Small cards share a row: two on a phone, three on a tablet and one more for every
// extra 480 dp. The grid fills the width it is given, so a desktop window gains cards per row
// instead of shrinking a fixed-width grid into its middle.
private const val SMALL_CARD_COLUMNS = 4
private const val MAX_CARDS_PER_ROW = 8
private val GRID_GAP = 12.dp
private val PHONE_MAX_WIDTH = 520.dp
private val TABLET_MAX_WIDTH = 880.dp
private val CARDS_PER_ROW_WIDTH_STEP = 480.dp

internal fun dashboardGridColumnCount(contentWidth: Dp): Int {
    val cardsPerRow = when {
        contentWidth < PHONE_MAX_WIDTH -> 2
        contentWidth < TABLET_MAX_WIDTH -> 3
        else -> 4 + ((contentWidth - TABLET_MAX_WIDTH) / CARDS_PER_ROW_WIDTH_STEP).toInt()
    }.fastCoerceAtMost(MAX_CARDS_PER_ROW)
    return cardsPerRow * SMALL_CARD_COLUMNS
}

internal data class DashboardGridPlacement(
    val column: Int,
    val top: Int,
    val height: Int,
)

internal fun placeDashboardGridItems(
    spans: List<Int>,
    heights: List<Int>,
    columnCount: Int,
    gap: Int,
): List<DashboardGridPlacement> {
    val pending = spans.indices.toMutableList()
    val placements = MutableList(spans.size) {
        DashboardGridPlacement(column = 0, top = 0, height = 0)
    }
    var top = 0
    while (pending.isNotEmpty()) {
        val row = mutableListOf<Int>()
        var used = 0
        var index = 0
        while (index < pending.size) {
            val item = pending[index]
            val span = spans[item].fastCoerceIn(1, columnCount)
            if (used + span <= columnCount) {
                row += item
                used += span
                pending.removeAt(index)
            } else {
                index++
            }
        }
        val rowHeight = row.maxOf { heights[it] }
        var column = 0
        for (item in row) {
            placements[item] = DashboardGridPlacement(
                column = column,
                top = top,
                height = rowHeight,
            )
            column += spans[item].fastCoerceIn(1, columnCount)
        }
        top += rowHeight + gap
    }
    return placements
}

private const val DRAGGING_WIDGET_SCALE = 1.02f
private const val SHAKE_DEGREES = 0.5f

@Composable
internal fun DashboardWidgetGrid(
    widgets: List<DashboardWidget>,
    isReorderable: Boolean,
    onOrderChange: (List<DashboardWidget>) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(widget: DashboardWidget) -> Unit = {},
    content: @Composable (widget: DashboardWidget) -> Unit,
) {
    var orderedWidgets by remember(widgets) { mutableStateOf(widgets) }
    val currentOnOrderChange by rememberUpdatedState(onOrderChange)

    val settledBounds = remember { mutableStateMapOf<DashboardWidget, Rect>() }
    var draggingWidget by remember { mutableStateOf<DashboardWidget?>(null) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var fingerPosition by remember { mutableStateOf(Offset.Zero) }

    val shakeRotation by rememberInfiniteTransition(label = "shake").animateFloat(
        initialValue = -SHAKE_DEGREES,
        targetValue = SHAKE_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(80),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rotation",
    )

    LookaheadScope {
        val lookaheadScope = this
        Layout(
            modifier = modifier,
            content = {
                for (widget in orderedWidgets) key(widget) {
                    val isDragging = widget == draggingWidget
                    val bounds = settledBounds[widget]

                    val placementModifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationX = fingerPosition.x - grabOffset.x - (bounds?.left ?: 0f)
                                translationY = fingerPosition.y - grabOffset.y - (bounds?.top ?: 0f)
                                scaleX = DRAGGING_WIDGET_SCALE
                                scaleY = DRAGGING_WIDGET_SCALE
                            }
                    } else {
                        Modifier
                            .animateBounds(lookaheadScope)
                            .graphicsLayer {
                                rotationZ = if (isReorderable) {
                                    shakeRotation
                                } else {
                                    0f
                                }
                            }
                    }

                    val dragShieldModifier: Modifier? = if (isReorderable) {
                        Modifier.pointerInput(widget) {
                            val onDragStart: (Offset) -> Unit = { offset ->
                                grabOffset = offset
                                fingerPosition =
                                    (settledBounds[widget]?.topLeft ?: Offset.Zero) + offset
                                draggingWidget = widget
                            }
                            val onDrag: (PointerInputChange, Offset) -> Unit = { change, dragAmount ->
                                change.consume()
                                fingerPosition += dragAmount
                                orderedWidgets = orderedWidgets.movedTowards(
                                    dragged = widget,
                                    fingerPosition = fingerPosition,
                                    settledBounds = settledBounds,
                                )
                            }
                            val onDragStop: () -> Unit = {
                                draggingWidget = null
                                currentOnOrderChange(orderedWidgets)
                            }

                            if (PlatformInfo.isAndroid) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = onDragStart,
                                    onDrag = onDrag,
                                    onDragEnd = onDragStop,
                                    onDragCancel = onDragStop,
                                )
                            } else {
                                detectDragGestures(
                                    onDragStart = onDragStart,
                                    onDrag = onDrag,
                                    onDragEnd = onDragStop,
                                    onDragCancel = onDragStop,
                                )
                            }
                        }
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .onPlaced { coordinates ->
                                with(lookaheadScope) {
                                    val root = lookaheadScopeCoordinates(coordinates)
                                    settledBounds[widget] = Rect(
                                        offset = root.localLookaheadPositionOf(coordinates),
                                        size = coordinates.toLookaheadCoordinates().size.toSize(),
                                    )
                                }
                            }
                            .then(placementModifier),
                    ) {
                        content(widget)
                        if (dragShieldModifier != null) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .then(dragShieldModifier),
                            )
                        }
                        overlay(widget)
                    }
                }
            },
        ) { measurables, constraints ->
            val gap = GRID_GAP.roundToPx()
            val gridWidth = constraints.maxWidth
            if (measurables.isEmpty() || gridWidth <= 0) {
                layout(gridWidth, 0) {}
            } else {
                // The bands are dp, so they mean the same card size whatever the screen density.
                val columnCount = dashboardGridColumnCount(contentWidth = gridWidth.toDp())
                val stride = (gridWidth + gap).toFloat() / columnCount
                val spans = orderedWidgets.map { it.width.cells.fastCoerceIn(1, columnCount) }
                val itemWidths = spans.map {
                    ((stride * it).roundToInt() - gap).fastCoerceAtLeast(0)
                }
                // A card may only be measured once, so its natural height is read through
                // intrinsic measurement; the tallest card of a row then sets the height that the
                // single measure stretches every card of that row to.
                val naturalHeights = measurables.mapIndexed { index, measurable ->
                    measurable.minIntrinsicHeight(itemWidths[index])
                }
                val placements = placeDashboardGridItems(spans, naturalHeights, columnCount, gap)
                val placeables = measurables.mapIndexed { index, measurable ->
                    val itemWidth = itemWidths[index]
                    val itemHeight = placements[index].height
                    measurable.measure(
                        Constraints(
                            minWidth = itemWidth,
                            maxWidth = itemWidth,
                            minHeight = itemHeight,
                            maxHeight = itemHeight,
                        ),
                    )
                }
                val gridHeight = placements.maxOf { it.top + it.height }
                layout(gridWidth, gridHeight) {
                    placements.forEachIndexed { index, placement ->
                        placeables[index].placeRelative(
                            x = (stride * placement.column).roundToInt(),
                            y = placement.top,
                        )
                    }
                }
            }
        }
    }
}

private fun List<DashboardWidget>.movedTowards(
    dragged: DashboardWidget,
    fingerPosition: Offset,
    settledBounds: Map<DashboardWidget, Rect>,
): List<DashboardWidget> {
    val from = indexOf(dragged)
    if (from < 0) return this
    val to = indexOfFirst { settledBounds[it]?.contains(fingerPosition) == true }
    if (to < 0 || to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
