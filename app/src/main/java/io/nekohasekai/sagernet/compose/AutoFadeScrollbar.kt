package io.nekohasekai.sagernet.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.ScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AutoFadeVerticalScrollbar(
    modifier: Modifier = Modifier,
    scrollState: LazyListState,
    fadeInDurationMs: Int = 150,
    fadeOutDurationMs: Int = 500,
    fadeOutDelayMs: Long = 1000L,
    style: ScrollbarStyle = defaultMaterialScrollbarStyle().copy(
        thickness = 12.dp,
    ),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    var isScrolling by remember { mutableStateOf(false) }
    var shouldShow by remember { mutableStateOf(false) }

    val isScrollInProgress by remember {
        derivedStateOf { scrollState.isScrollInProgress }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { isScrollInProgress }
            .collectLatest { scrolling ->
                isScrolling = scrolling
                if (scrolling) {
                    shouldShow = true
                }
            }
    }

    LaunchedEffect(isDragging, isHovered, isScrolling) {
        if (isDragging || isHovered || isScrolling) {
            shouldShow = true
        } else {
            delay(fadeOutDelayMs)
            shouldShow = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (shouldShow) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (shouldShow) fadeInDurationMs else fadeOutDurationMs,
        ),
        label = "scrollbar_alpha",
    )

    val adapter = rememberScrollbarAdapter(scrollState = scrollState)
    VerticalScrollbar(
        modifier = modifier
            .fillMaxHeight()
            .alpha(alpha),
        adapter = adapter,
        style = style,
        interactionSource = interactionSource,
    )
}
