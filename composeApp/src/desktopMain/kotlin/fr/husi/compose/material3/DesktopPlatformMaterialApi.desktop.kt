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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.zIndex
import fr.husi.database.DataStore
import fr.husi.database.preference.getBlocking
import fr.husi.database.preference.setBlocking
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

internal object DesktopPlatformMaterialApi : PlatformMaterialApi by standardPlatformMaterialApi() {
    @Composable
    override fun NavigationSuite(
        items: ImmutableList<NavigationSuiteItem>,
        showNavigation: Boolean,
        snackbarHost: @Composable () -> Unit,
        floatingActionButton: @Composable () -> Unit,
        content: @Composable () -> Unit,
    ) {
        // Desktop windows have enough space that collapsing the rail is not worth a toggle.
        val railState = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)
        var railWidth by remember {
            mutableStateOf(DataStore.desktopNavRailWidth.getBlocking().dp)
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
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
                                    icon = { NavigationSuiteItemIcon(item) },
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
                            DataStore.desktopNavRailWidth.setBlocking(railWidth.value.roundToInt())
                        },
                    )
                }
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
 * [Cursor.E_RESIZE_CURSOR] displays left and right arrow on Windows,
 * While on other platform, it only displays on direction arrow.
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
    val centerX = width / 2f
    val centerY = height / 2f
    val image = Surface.makeRasterN32Premul(width, height).use { surface ->
        val canvas = surface.canvas
        canvas.clear(Color.TRANSPARENT)
        Paint().use { paint ->
            paint.isAntiAlias = true
            paint.strokeJoin = PaintStrokeJoin.MITER
            paint.strokeMiter = 10f
            PathBuilder().use { arrow ->
                val tip = width * 0.46f
                val neck = width * 0.22f
                val bar = height * 0.11f
                val head = height * 0.34f
                arrow.moveTo(centerX - tip, centerY)
                arrow.lineTo(centerX - neck, centerY - head)
                arrow.lineTo(centerX - neck, centerY - bar)
                arrow.lineTo(centerX + neck, centerY - bar)
                arrow.lineTo(centerX + neck, centerY - head)
                arrow.lineTo(centerX + tip, centerY)
                arrow.lineTo(centerX + neck, centerY + head)
                arrow.lineTo(centerX + neck, centerY + bar)
                arrow.lineTo(centerX - neck, centerY + bar)
                arrow.lineTo(centerX - neck, centerY + head)
                arrow.closePath()

                arrow.detach().use { path ->
                    paint.mode = PaintMode.STROKE
                    paint.strokeWidth = (width / 24f).fastCoerceAtLeast(1f)
                    paint.strokeCap = PaintStrokeCap.SQUARE
                    paint.color = Color.WHITE
                    canvas.drawPath(path, paint)
                    paint.mode = PaintMode.FILL
                    paint.color = Color.BLACK
                    canvas.drawPath(path, paint)
                }
            }

            val barHalfWidth = (width / 36f).fastCoerceAtLeast(0.5f)
            val barHalfHeight = height * 0.46f
            val bar = Rect.makeLTRB(
                centerX - barHalfWidth, centerY - barHalfHeight,
                centerX + barHalfWidth, centerY + barHalfHeight,
            )
            paint.mode = PaintMode.STROKE
            paint.strokeWidth = (width / 12f).fastCoerceAtLeast(2f)
            paint.strokeCap = PaintStrokeCap.BUTT
            paint.color = Color.WHITE
            canvas.drawRect(bar, paint)
            paint.mode = PaintMode.FILL
            paint.color = Color.BLACK
            canvas.drawRect(bar, paint)
        }
        surface.makeImageSnapshot().use { it.toComposeImageBitmap().toAwtImage() }
    }
    return runCatching {
        PointerIcon(
            toolkit.createCustomCursor(image, Point(width / 2, height / 2), "col-resize"),
        )
    }.getOrElse {
        PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
    }
}
