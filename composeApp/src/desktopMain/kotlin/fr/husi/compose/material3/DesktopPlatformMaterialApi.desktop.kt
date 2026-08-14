package fr.husi.compose.material3

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.husi.database.DataStore
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import java.awt.geom.Rectangle2D

internal object DesktopPlatformMaterialApi : PlatformMaterialApi by standardPlatformMaterialApi() {
    @Composable
    override fun NavigationSuite(
        items: ImmutableList<NavigationSuiteItem>,
        showNavigation: Boolean,
        content: @Composable () -> Unit,
    ) {
        // Desktop windows have enough space that collapsing the rail is not worth a toggle.
        val railState = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)
        var railWidth by remember {
            mutableStateOf(DataStore.desktopNavRailWidth.dp)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerWidth = maxWidth
            fun clampWidth(width: Dp) = width.coerceIn(
                160.dp,
                minOf(360.dp, containerWidth * 0.45f).coerceAtLeast(160.dp),
            )

            val displayedWidth = clampWidth(railWidth)
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    WideNavigationRail(
                        modifier = Modifier.width(displayedWidth),
                        state = railState,
                        arrangement = Arrangement.Top,
                    ) {
                        items.forEach { item ->
                            WideNavigationRailItem(
                                selected = item.selected,
                                onClick = item.onClick,
                                icon = {
                                    Icon(
                                        imageVector = vectorResource(item.icon),
                                        contentDescription = stringResource(item.label),
                                    )
                                },
                                label = { Text(stringResource(item.label)) },
                                railExpanded = true,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        content()
                    }
                }
                DesktopNavRailResizeHandle(
                    modifier = Modifier
                        .align(
                            if (isRtl) {
                                Alignment.CenterEnd
                            } else {
                                Alignment.CenterStart
                            },
                        )
                        .offset(
                            x = if (isRtl) {
                                -(displayedWidth - 6.dp)
                            } else {
                                displayedWidth - 6.dp
                            },
                        )
                        .zIndex(1f),
                    onDrag = { delta ->
                        railWidth = clampWidth(
                            railWidth + if (isRtl) {
                                -delta
                            } else {
                                delta
                            },
                        )
                    },
                    onDragFinished = {
                        DataStore.desktopNavRailWidth = railWidth.value.roundToInt()
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopNavRailResizeHandle(
    onDrag: (Dp) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val dividerColor by animateColorAsState(
        targetValue = if (hovered || dragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    )
    val draggableState = rememberDraggableState { deltaPx ->
        currentOnDrag(with(density) { deltaPx.toDp() })
    }
    val resizeIcon = remember { horizontalResizePointerIcon() }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .pointerHoverIcon(resizeIcon)
            .hoverable(interactionSource)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = { dragging = true },
                onDragStopped = {
                    dragging = false
                    currentOnDragFinished()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = dividerColor,
        )
    }
}

/**
 * [java.awt.Cursor.E_RESIZE_CURSOR] only displays left and right arrow on Windows.
 * So we draw it ourselves to make sure it always left and right arrow.
 *
 * Translate from: [adwaita.svg - lc-resize](https://gitlab.gnome.org/GNOME/adwaita-icon-theme/-/blob/d78e7194cd8319914959e2abd40442108fe2805f/src/cursors/adwaita.svg#L11701)
 * with [LGPL](https://gitlab.gnome.org/GNOME/adwaita-icon-theme/-/raw/d78e7194cd8319914959e2abd40442108fe2805f/COPYING_LGPL)
 */
private fun horizontalResizePointerIcon(): PointerIcon {
    val toolkit = Toolkit.getDefaultToolkit()
    val best = toolkit.getBestCursorSize(48, 48)
    val width = if (best.width > 0) best.width else 48
    val height = if (best.height > 0) best.height else 48
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val centerX = width / 2f
    val centerY = height / 2f

    val arrow = Path2D.Float().apply {
        val tip = width * 0.46f
        val neck = width * 0.22f
        val bar = height * 0.11f
        val head = height * 0.34f
        moveTo(centerX - tip, centerY)
        lineTo(centerX - neck, centerY - head)
        lineTo(centerX - neck, centerY - bar)
        lineTo(centerX + neck, centerY - bar)
        lineTo(centerX + neck, centerY - head)
        lineTo(centerX + tip, centerY)
        lineTo(centerX + neck, centerY + head)
        lineTo(centerX + neck, centerY + bar)
        lineTo(centerX - neck, centerY + bar)
        lineTo(centerX - neck, centerY + head)
        closePath()
    }

    graphics.stroke = BasicStroke(
        (width / 24f).coerceAtLeast(1f),
        BasicStroke.CAP_SQUARE,
        BasicStroke.JOIN_MITER,
        10f,
    )
    graphics.color = Color.WHITE
    graphics.draw(arrow)
    graphics.color = Color.BLACK
    graphics.fill(arrow)

    val barHalfW = (width / 36f).coerceAtLeast(0.5f)
    val barHalfH = height * 0.46f
    val bar = Rectangle2D.Float(
        centerX - barHalfW, centerY - barHalfH,
        barHalfW * 2, barHalfH * 2,
    )
    graphics.stroke = BasicStroke(
        (width / 12f).coerceAtLeast(2f),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER,
        10f,
    )
    graphics.color = Color.WHITE
    graphics.draw(bar)
    graphics.color = Color.BLACK
    graphics.fill(bar)

    graphics.dispose()
    return runCatching {
        PointerIcon(
            toolkit.createCustomCursor(image, Point(width / 2, height / 2), "col-resize"),
        )
    }.getOrElse {
        PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
    }
}