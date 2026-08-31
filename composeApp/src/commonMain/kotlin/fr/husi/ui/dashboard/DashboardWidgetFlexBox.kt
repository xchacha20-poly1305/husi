@file:OptIn(ExperimentalFlexBoxApi::class)

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
import androidx.compose.foundation.layout.ExperimentalFlexBoxApi
import androidx.compose.foundation.layout.FlexAlignItems
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexDirection
import androidx.compose.foundation.layout.FlexWrap
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
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.lookaheadScopeCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import fr.husi.platform.PlatformInfo

private const val DRAGGING_WIDGET_SCALE = 1.02f
private const val SHAKE_DEGREES = 0.5f

@Composable
internal fun DashboardWidgetFlexBox(
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
        FlexBox(
            modifier = modifier,
            config = {
                direction(FlexDirection.Row)
                wrap(FlexWrap.Wrap)
                alignItems(FlexAlignItems.Stretch)
                gap(12.dp)
            },
        ) {
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
                            rotationZ = if (isReorderable) shakeRotation else 0f
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
                        .flex {
                            when (widget.width) {
                                DashboardWidgetWidth.Half -> basis(240.dp)
                                DashboardWidgetWidth.Full -> basis(1f)
                            }
                            grow(1f)
                            shrink(1f)
                        }
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
                        Box(modifier = Modifier.matchParentSize().then(dragShieldModifier))
                    }
                    overlay(widget)
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
