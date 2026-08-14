package fr.husi.compose.material3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.PrimaryScrollableTabRow as MaterialPrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow as MaterialPrimaryTabRow
import androidx.compose.material3.Tab as MaterialTab
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LocalContentColor as MaterialLocalContentColor
import androidx.compose.material3.LocalTextStyle as MaterialLocalTextStyle
import androidx.compose.material3.Text as MaterialText
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.LocalTextStyle as TvLocalTextStyle
import androidx.tv.material3.DrawerValue as TvDrawerValue
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Checkbox as TvCheckbox
import androidx.tv.material3.contentColorFor as tvContentColorFor
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.IconButtonDefaults as TvIconButtonDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.RadioButton as TvRadioButton
import androidx.tv.material3.NavigationDrawer as TvNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem as TvNavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.SurfaceDefaults as TvSurfaceDefaults
import androidx.tv.material3.Switch as TvSwitch
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.rememberDrawerState as rememberTvDrawerState

internal val LocalTvNavigationDrawerScope =
    staticCompositionLocalOf<NavigationDrawerScope?> { null }

internal object TvPlatformMaterialApi : PlatformMaterialApi {
    @Composable
    private fun tvButtonContainerColor(color: Color): Color =
        if (color == Color.Unspecified) {
            TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        } else {
            color
        }

    @Composable
    private fun tvButtonContentColor(color: Color): Color =
        if (color == Color.Unspecified) {
            TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        } else {
            color
        }

    @Composable
    private fun tvIconButtonContainerColor(color: Color): Color =
        if (color == Color.Unspecified) {
            TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        } else {
            color
        }

    @Composable
    private fun tvIconButtonContentColor(color: Color): Color =
        if (color == Color.Unspecified) {
            TvMaterialTheme.colorScheme.onSurface
        } else {
            color
        }

    @Composable
    private fun tvCardContainerColor(color: Color): Color =
        if (color == Color.Unspecified) {
            TvMaterialTheme.colorScheme.surfaceVariant
        } else {
            color
        }

    @Composable
    private fun tvSurfaceContainerColor(color: Color): Color =
        if (color == Color.Unspecified) {
            TvMaterialTheme.colorScheme.surface
        } else {
            color
        }

    @Composable
    private fun tvResolvedContentColor(
        containerColor: Color,
        contentColor: Color,
    ): Color = if (contentColor == Color.Unspecified) {
        tvContentColorFor(containerColor)
    } else {
        contentColor
    }

    @Composable
    override fun NavigationSuite(
        items: ImmutableList<NavigationSuiteItem>,
        showNavigation: Boolean,
        content: @Composable () -> Unit,
    ) {
        val drawerState = rememberTvDrawerState(TvDrawerValue.Open)
        TvNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { drawerValue ->
                CompositionLocalProvider(LocalTvNavigationDrawerScope provides this) {
                    val drawerWidth = when (drawerValue) {
                        TvDrawerValue.Closed -> NavigationDrawerItemDefaults.CollapsedDrawerItemWidth
                        TvDrawerValue.Open -> NavigationDrawerItemDefaults.ExpandedDrawerItemWidth
                    }
                    TvSurface(
                        modifier = Modifier
                            .width(drawerWidth)
                            .fillMaxHeight(),
                    ) {
                        Column {
                            items.forEach { item ->
                                TvNavigationSuiteItem(item)
                            }
                        }
                    }
                }
            },
            content = content,
        )
    }

    @Composable
    private fun TvNavigationSuiteItem(item: NavigationSuiteItem) {
        val tvScope = LocalTvNavigationDrawerScope.current
        if (tvScope != null) with(tvScope) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(item.selected, hasFocus) {
                if (item.selected && hasFocus) {
                    focusRequester.requestFocus()
                }
            }
            TvNavigationDrawerItem(
                selected = item.selected,
                onClick = item.onClick,
                leadingContent = {
                    ProvideTvMaterialBridge {
                        MaterialIcon(
                            imageVector = vectorResource(item.icon),
                            contentDescription = stringResource(item.label),
                        )
                    }
                },
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                ProvideTvMaterialBridge {
                    MaterialText(stringResource(item.label))
                }
            }
        }
    }

    @Composable
    override fun Text(
        text: String,
        modifier: Modifier,
        color: Color,
        fontSize: TextUnit,
        fontStyle: FontStyle?,
        fontWeight: FontWeight?,
        fontFamily: FontFamily?,
        letterSpacing: TextUnit,
        textDecoration: TextDecoration?,
        textAlign: TextAlign?,
        lineHeight: TextUnit,
        overflow: TextOverflow,
        softWrap: Boolean,
        maxLines: Int,
        minLines: Int,
        onTextLayout: (TextLayoutResult) -> Unit,
        style: TextStyle?,
    ) {
        MaterialText(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style ?: MaterialLocalTextStyle.current,
        )
    }

    @Composable
    override fun Text(
        text: AnnotatedString,
        modifier: Modifier,
        color: Color,
        fontSize: TextUnit,
        fontStyle: FontStyle?,
        fontWeight: FontWeight?,
        fontFamily: FontFamily?,
        letterSpacing: TextUnit,
        textDecoration: TextDecoration?,
        textAlign: TextAlign?,
        lineHeight: TextUnit,
        overflow: TextOverflow,
        softWrap: Boolean,
        maxLines: Int,
        minLines: Int,
        onTextLayout: (TextLayoutResult) -> Unit,
        style: TextStyle?,
    ) {
        MaterialText(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style ?: MaterialLocalTextStyle.current,
        )
    }

    @Composable
    override fun Icon(
        imageVector: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        tint: Color?,
    ) {
        MaterialIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint ?: MaterialLocalContentColor.current,
        )
    }

    @Composable
    override fun Icon(
        bitmap: ImageBitmap,
        contentDescription: String?,
        modifier: Modifier,
        tint: Color?,
    ) {
        MaterialIcon(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint ?: MaterialLocalContentColor.current,
        )
    }

    @Composable
    override fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier,
        tint: Color?,
    ) {
        MaterialIcon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint ?: MaterialLocalContentColor.current,
        )
    }

    @Composable
    override fun Button(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        shape: Shape?,
        containerColor: Color,
        contentColor: Color,
        border: BorderStroke?,
        contentPadding: PaddingValues?,
        content: @Composable RowScope.() -> Unit,
    ) {
        val buttonShape = shape ?: TvMaterialTheme.shapes.medium
        val resolvedContainerColor = tvButtonContainerColor(containerColor)
        val resolvedContentColor = tvButtonContentColor(contentColor)
        TvButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = if (shape == null) {
                TvButtonDefaults.shape()
            } else {
                TvButtonDefaults.shape(shape = buttonShape)
            },
            colors = TvButtonDefaults.colors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
            ),
            border = if (border == null) {
                TvButtonDefaults.border()
            } else {
                TvButtonDefaults.border(border = border.toTvBorder(buttonShape))
            },
            contentPadding = contentPadding ?: TvButtonDefaults.ContentPadding,
            content = {
                ProvideTvMaterialBridge {
                    content()
                }
            },
        )
    }

    @Composable
    override fun Checkbox(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
    ) {
        TvCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }

    @Composable
    override fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
        thumbContent: (@Composable () -> Unit)?,
    ) {
        TvSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            thumbContent = thumbContent,
        )
    }

    @Composable
    override fun RadioButton(
        selected: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
    ) {
        TvRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    }

    @Composable
    override fun IconButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        containerColor: Color,
        contentColor: Color,
        content: @Composable () -> Unit,
    ) {
        val resolvedContainerColor = tvIconButtonContainerColor(containerColor)
        val resolvedContentColor = tvIconButtonContentColor(contentColor)
        TvIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = TvIconButtonDefaults.colors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
            ),
            content = {
                ProvideTvMaterialBridge(content)
            },
        )
    }

    @Composable
    override fun Card(
        modifier: Modifier,
        elevated: Boolean,
        shape: Shape?,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        border: BorderStroke?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val cardShape = shape ?: TvMaterialTheme.shapes.medium
        val resolvedContainerColor = tvCardContainerColor(color)
        val resolvedContentColor = tvResolvedContentColor(resolvedContainerColor, contentColor)
        TvSurface(
            modifier = modifier,
            tonalElevation = tonalElevation.takeIf { it != Dp.Unspecified } ?: 0.dp,
            shape = cardShape,
            colors = TvSurfaceDefaults.colors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
            ),
            border = border.toTvBorder(cardShape),
        ) {
            ProvideTvMaterialBridge {
                Column(content = content)
            }
        }
    }

    @Composable
    override fun Card(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        elevated: Boolean,
        shape: Shape?,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        border: BorderStroke?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val cardShape = shape ?: TvMaterialTheme.shapes.medium
        val resolvedContainerColor = tvCardContainerColor(color)
        val resolvedContentColor = tvResolvedContentColor(resolvedContainerColor, contentColor)
        TvCard(
            onClick = onClick,
            modifier = modifier,
            onLongClick = { },
            shape = if (shape == null) {
                TvCardDefaults.shape()
            } else {
                TvCardDefaults.shape(shape = cardShape)
            },
            colors = TvCardDefaults.colors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
            ),
            scale = TvCardDefaults.scale(),
            border = if (border == null) {
                TvCardDefaults.border()
            } else {
                TvCardDefaults.border(border = border.toTvBorder(cardShape))
            },
            glow = TvCardDefaults.glow(),
            interactionSource = null,
            content = {
                ProvideTvMaterialBridge {
                    content()
                }
            },
        )
    }

    @Composable
    override fun Surface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        shadowElevation: Dp,
        border: BorderStroke?,
        content: @Composable () -> Unit,
    ) {
        val resolvedContainerColor = tvSurfaceContainerColor(color)
        val resolvedContentColor = tvResolvedContentColor(resolvedContainerColor, contentColor)
        TvSurface(
            modifier = modifier,
            tonalElevation = tonalElevation.takeIf { it != Dp.Unspecified } ?: 0.dp,
            shape = shape,
            colors = TvSurfaceDefaults.colors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
            ),
            border = border.toTvBorder(shape),
        ) {
            ProvideTvMaterialBridge(content)
        }
    }

    @Composable
    override fun Surface(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        shape: Shape,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        shadowElevation: Dp,
        border: BorderStroke?,
        content: @Composable () -> Unit,
    ) {
        val resolvedContainerColor = tvSurfaceContainerColor(color)
        val resolvedContentColor = tvResolvedContentColor(resolvedContainerColor, contentColor)
        TvSurface(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            tonalElevation = tonalElevation.takeIf { it != Dp.Unspecified } ?: 0.dp,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
            ),
            border = ClickableSurfaceDefaults.border(
                border = border.toTvBorder(shape),
            ),
        ) {
            ProvideTvMaterialBridge(content)
        }
    }

    @Composable
    override fun PrimaryTabRow(
        selectedTabIndex: Int,
        modifier: Modifier,
        containerColor: Color,
        content: @Composable () -> Unit,
    ) {
        MaterialPrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = modifier,
            containerColor = containerColor,
        ) {
            CompositionLocalProvider(
                LocalPlatformTabRowScope provides TvPlatformTabRowScope,
            ) {
                content()
            }
        }
    }

    @Composable
    override fun PrimaryScrollableTabRow(
        selectedTabIndex: Int,
        modifier: Modifier,
        containerColor: Color,
        edgePadding: Dp,
        content: @Composable () -> Unit,
    ) {
        MaterialPrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = modifier,
            containerColor = containerColor,
            edgePadding = edgePadding,
        ) {
            CompositionLocalProvider(
                LocalPlatformTabRowScope provides TvPlatformTabRowScope,
            ) {
                content()
            }
        }
    }

    private object TvPlatformTabRowScope : PlatformTabRowScope {
        @Composable
        override fun Tab(
            selected: Boolean,
            onClick: () -> Unit,
            modifier: Modifier,
            enabled: Boolean,
            text: (@Composable () -> Unit)?,
            icon: (@Composable () -> Unit)?,
        ) {
            MaterialTab(
                selected = selected,
                onClick = onClick,
                modifier = modifier.onFocusChanged {
                    if (it.isFocused) {
                        onClick()
                    }
                },
                enabled = enabled,
                text = text,
                icon = icon,
            )
        }
    }

    private fun BorderStroke?.toTvBorder(shape: Shape): Border {
        return if (this == null) {
            Border.None
        } else {
            Border(border = this, shape = shape)
        }
    }
}
