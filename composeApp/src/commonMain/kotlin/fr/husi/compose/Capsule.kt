@file:OptIn(ExperimentalMaterial3Api::class)

package fr.husi.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3

object CapsuleDefaults {
    val Size: Dp get() = 44.dp
    val Shape: Shape get() = RoundedCornerShape(Size / 2)
    val HorizontalPadding: Dp get() = 16.dp
    val VerticalPadding: Dp get() = 8.dp
    val Spacing: Dp get() = 8.dp

    val borderColor: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)

    @Composable
    fun blurStyle(tintColor: Color = MaterialTheme.colorScheme.surfaceContainer): HazeBlurStyle {
        return HazeBlurStyle.Material3(containerColor = MaterialTheme.colorScheme.surface) {
            colorEffects(listOf(HazeColorEffect.tint(tintColor.copy(alpha = 0.5f))))
            fallbackColorEffect(HazeColorEffect.tint(tintColor.copy(alpha = 1f)))
        }
    }
}

private val CapsuleBarHeight get() = CapsuleDefaults.Size + CapsuleDefaults.VerticalPadding * 2

class CapsuleActionsScope internal constructor(
    rowScope: RowScope,
    private val hazeState: HazeState?,
) : RowScope by rowScope {

    @Composable
    fun CapsuleActionButton(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        CapsuleSurface(
            modifier = modifier.size(CapsuleDefaults.Size),
            hazeState = hazeState,
        ) {
            content()
        }
    }
}

@Composable
fun CapsuleSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    shape: Shape = CapsuleDefaults.Shape,
    borderColor: Color = CapsuleDefaults.borderColor,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val fillColor = if (hazeState != null) {
        Color.Transparent
    } else {
        CapsuleDefaults.containerColor
    }
    Surface(
        modifier = modifier.then(
            if (hazeState != null) {
                Modifier
                    .clip(shape)
                    .hazeBlur(
                        input = HazeInput.Backdrop(hazeState),
                        style = CapsuleDefaults.blurStyle(),
                    )
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        val fill: @Composable () -> Unit = {
            Box(
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
        if (onClick != null) {
            Surface(
                onClick = onClick,
                shape = shape,
                color = fillColor,
                content = fill,
            )
        } else {
            Surface(
                shape = shape,
                color = fillColor,
                content = fill,
            )
        }
    }
}

@Composable
fun CapsuleTopBar(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    actions: @Composable CapsuleActionsScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    capsuleSpacing: Dp = CapsuleDefaults.Spacing,
) {
    CapsuleBarLayout(
        modifier = modifier,
        hazeState = hazeState,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
        capsuleSpacing = capsuleSpacing,
        actions = actions,
    ) {
        if (title != null) {
            Box(modifier = Modifier.weight(1f)) {
                PillCapsule(hazeState = hazeState) {
                    title()
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CapsuleBarLayout(
    modifier: Modifier,
    hazeState: HazeState?,
    navigationIcon: (@Composable () -> Unit)?,
    windowInsets: WindowInsets,
    scrollBehavior: TopAppBarScrollBehavior?,
    capsuleSpacing: Dp,
    actions: @Composable CapsuleActionsScope.() -> Unit,
    center: @Composable RowScope.() -> Unit,
) {
    SetHeightOffsetLimit(scrollBehavior)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CapsuleDefaults.HorizontalPadding,
                    vertical = CapsuleDefaults.VerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(capsuleSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                CapsuleSurface(
                    modifier = Modifier.size(CapsuleDefaults.Size),
                    hazeState = hazeState,
                ) {
                    navigationIcon()
                }
            }

            center()

            Row(
                horizontalArrangement = Arrangement.spacedBy(capsuleSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleActionsScope(this, hazeState).actions()
            }
        }
    }
}

@Composable
private fun PillCapsule(
    hazeState: HazeState?,
    content: @Composable () -> Unit,
) {
    CapsuleSurface(
        modifier = Modifier.height(CapsuleDefaults.Size),
        hazeState = hazeState,
        onClick = {},
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            MarqueeWithFadingEdges {
                content()
            }
        }
    }
}

@Composable
fun CapsuleSearchTopBar(
    hazeState: HazeState?,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    onSearchPillClick: (() -> Unit)? = null,
    onSearchPillLongPress: (() -> Unit)? = null,
    actions: @Composable CapsuleActionsScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    capsuleSpacing: Dp = CapsuleDefaults.Spacing,
) {
    CapsuleBarLayout(
        modifier = modifier,
        hazeState = hazeState,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
        capsuleSpacing = capsuleSpacing,
        actions = actions,
    ) {
        CapsuleSearchPill(
            modifier = Modifier.weight(1f),
            hazeState = hazeState,
            onClick = onSearchPillClick,
            onLongClick = onSearchPillLongPress,
        ) {
            inputField()
        }
    }
}

@Composable
fun CapsuleHeader(
    hazeState: HazeState,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.fastCoerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeBlur(
                input = HazeInput.Backdrop(hazeState),
                style = CapsuleDefaults.blurStyle(tintColor = appBarContainerColor),
            ),
        content = content,
    )
}

@Composable
fun CapsuleSearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val showCenteredPlaceholder = searchBarState.currentValue == SearchBarValue.Collapsed &&
            textFieldState.text.isEmpty()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchBarDefaults.InputFieldHeight),
        contentAlignment = Alignment.Center,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = onSearch,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .requiredHeight(SearchBarDefaults.InputFieldHeight),
            placeholder = placeholder?.let { content ->
                if (showCenteredPlaceholder) {
                    { Box(modifier = Modifier.alpha(0f)) { content() } }
                } else {
                    content
                }
            },
            leadingIcon = leadingIcon?.let { content ->
                if (showCenteredPlaceholder) {
                    { Box(modifier = Modifier.alpha(0f)) { content() } }
                } else {
                    content
                }
            },
            trailingIcon = trailingIcon,
        )

        if (showCenteredPlaceholder && (placeholder != null || leadingIcon != null)) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    leadingIcon?.invoke()
                    if (leadingIcon != null && placeholder != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                        placeholder?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleSearchPill(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val currentOnClick = rememberUpdatedState(onClick)
    val currentOnLongClick = rememberUpdatedState(onLongClick)
    CapsuleSurface(
        modifier = modifier.height(CapsuleDefaults.Size),
        hazeState = hazeState,
    ) {
        content()
        if (onClick != null || onLongClick != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { currentOnClick.value?.invoke() },
                            onLongPress = { currentOnLongClick.value?.invoke() },
                        )
                    },
            )
        }
    }
}

@Composable
private fun SetHeightOffsetLimit(scrollBehavior: TopAppBarScrollBehavior?) {
    if (scrollBehavior == null) return
    val heightOffsetLimit = with(LocalDensity.current) { -CapsuleBarHeight.toPx() }
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != heightOffsetLimit) {
            scrollBehavior.state.heightOffsetLimit = heightOffsetLimit
        }
    }
}

@Composable
private fun MarqueeWithFadingEdges(content: @Composable () -> Unit) {
    var contentWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }
    val overflowing = containerWidth in 1 until contentWidth

    Box(
        modifier = Modifier
            .onSizeChanged { containerWidth = it.width }
            .then(
                if (overflowing) {
                    Modifier.fadingEdge(
                        orientation = Orientation.Horizontal,
                        length = 16.dp,
                        fadeStart = true,
                        fadeEnd = true,
                    )
                } else {
                    Modifier
                },
            )
            .basicMarquee(),
    ) {
        Box(modifier = Modifier.onSizeChanged { contentWidth = it.width }) {
            content()
        }
    }
}
