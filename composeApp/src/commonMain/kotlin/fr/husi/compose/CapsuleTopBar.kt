@file:OptIn(ExperimentalMaterial3Api::class)

package fr.husi.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CapsuleSize get() = 44.dp
private val CapsuleBarVerticalPadding get() = 8.dp
private val CapsuleBarHeight get() = CapsuleSize + CapsuleBarVerticalPadding * 2

@Composable
fun CapsuleTopBar(
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    capsuleSpacing: Dp = 8.dp,
) {
    SetHeightOffsetLimit(scrollBehavior)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .then(
                scrollBehavior?.let {
                    Modifier.nestedScroll(it.nestedScrollConnection)
                } ?: Modifier,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(capsuleSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                CircularCapsule {
                    navigationIcon()
                }
            }

            if (title != null) {
                PillCapsule {
                    title()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(capsuleSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun CircularCapsule(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(CapsuleSize),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
        ) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PillCapsule(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.height(CapsuleSize),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
fun CapsuleActionButton(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(CapsuleSize),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
        ) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
fun CapsuleSearchTopBar(
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    capsuleSpacing: Dp = 8.dp,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(capsuleSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                CapsuleCircular {
                    navigationIcon()
                }
            }

            CapsuleSearchPill(modifier = Modifier.weight(1f)) {
                inputField()
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(capsuleSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
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
private fun CapsuleCircular(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(CapsuleSize),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun CapsuleSearchPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.height(CapsuleSize),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
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
