package fr.husi.compose.material3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults as MaterialCardDefaults
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults as MaterialButtonDefaults
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButtonColors as MaterialIconButtonColors
import androidx.compose.material3.LocalTextStyle as MaterialLocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.PrimaryScrollableTabRow as MaterialPrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow as MaterialPrimaryTabRow
import androidx.compose.material3.RadioButton as MaterialRadioButton
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.Tab as MaterialTab
import androidx.compose.material3.TabRowDefaults as MaterialTabRowDefaults
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
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
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext

@Immutable
class NavigationSuiteItem(
    val label: StringResource,
    val icon: DrawableResource,
    val selected: Boolean,
    val onClick: () -> Unit,
)

internal interface PlatformTabRowScope {
    @Composable
    fun Tab(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        text: (@Composable () -> Unit)? = null,
        icon: (@Composable () -> Unit)? = null,
    )
}

internal interface PlatformMaterialApi {
    @Composable
    fun NavigationSuite(
        items: ImmutableList<NavigationSuiteItem>,
        showNavigation: Boolean,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Text(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        fontSize: TextUnit = TextUnit.Unspecified,
        fontStyle: FontStyle? = null,
        fontWeight: FontWeight? = null,
        fontFamily: FontFamily? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        textDecoration: TextDecoration? = null,
        textAlign: TextAlign? = null,
        lineHeight: TextUnit = TextUnit.Unspecified,
        overflow: TextOverflow = TextOverflow.Clip,
        softWrap: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
        minLines: Int = 1,
        onTextLayout: (TextLayoutResult) -> Unit = {},
        style: TextStyle? = null,
    )

    @Composable
    fun Text(
        text: AnnotatedString,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        fontSize: TextUnit = TextUnit.Unspecified,
        fontStyle: FontStyle? = null,
        fontWeight: FontWeight? = null,
        fontFamily: FontFamily? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        textDecoration: TextDecoration? = null,
        textAlign: TextAlign? = null,
        lineHeight: TextUnit = TextUnit.Unspecified,
        overflow: TextOverflow = TextOverflow.Clip,
        softWrap: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
        minLines: Int = 1,
        onTextLayout: (TextLayoutResult) -> Unit = {},
        style: TextStyle? = null,
    )

    @Composable
    fun Icon(
        imageVector: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        tint: Color? = null,
    )

    @Composable
    fun Icon(
        bitmap: ImageBitmap,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        tint: Color? = null,
    )

    @Composable
    fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        tint: Color? = null,
    )

    @Composable
    fun Button(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        shape: Shape? = null,
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        border: BorderStroke? = null,
        contentPadding: PaddingValues? = null,
        content: @Composable RowScope.() -> Unit,
    )

    @Composable
    fun Checkbox(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    )

    @Composable
    fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        thumbContent: (@Composable () -> Unit)? = null,
    )

    @Composable
    fun RadioButton(
        selected: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    )

    @Composable
    fun IconButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Card(
        modifier: Modifier = Modifier,
        elevated: Boolean = false,
        shape: Shape? = null,
        color: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        tonalElevation: Dp = Dp.Unspecified,
        border: BorderStroke? = null,
        content: @Composable ColumnScope.() -> Unit,
    )

    @Composable
    fun Card(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        elevated: Boolean = false,
        shape: Shape? = null,
        color: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        tonalElevation: Dp = Dp.Unspecified,
        border: BorderStroke? = null,
        content: @Composable ColumnScope.() -> Unit,
    )

    @Composable
    fun Surface(
        modifier: Modifier = Modifier,
        shape: Shape = RectangleShape,
        color: Color = MaterialTheme.colorScheme.surface,
        contentColor: Color = contentColorFor(color),
        tonalElevation: Dp = Dp.Unspecified,
        shadowElevation: Dp = Dp.Unspecified,
        border: BorderStroke? = null,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Surface(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        shape: Shape = RectangleShape,
        color: Color = MaterialTheme.colorScheme.surface,
        contentColor: Color = contentColorFor(color),
        tonalElevation: Dp = Dp.Unspecified,
        shadowElevation: Dp = Dp.Unspecified,
        border: BorderStroke? = null,
        content: @Composable () -> Unit,
    )

    @Composable
    fun PrimaryTabRow(
        selectedTabIndex: Int,
        modifier: Modifier = Modifier,
        containerColor: Color = MaterialTabRowDefaults.primaryContainerColor,
        content: @Composable () -> Unit,
    )

    @Composable
    fun PrimaryScrollableTabRow(
        selectedTabIndex: Int,
        modifier: Modifier = Modifier,
        containerColor: Color = MaterialTabRowDefaults.primaryContainerColor,
        edgePadding: Dp = MaterialTabRowDefaults.ScrollableTabRowEdgeStartPadding,
        content: @Composable () -> Unit,
    )
}

private object MaterialPlatformTabRowScope : PlatformTabRowScope {
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
            modifier = modifier,
            enabled = enabled,
            text = text,
            icon = icon,
        )
    }
}

internal val LocalPlatformTabRowScope = staticCompositionLocalOf<PlatformTabRowScope> {
    MaterialPlatformTabRowScope
}

private object MaterialPlatformMaterialApi : PlatformMaterialApi {
    @Composable
    override fun NavigationSuite(
        items: ImmutableList<NavigationSuiteItem>,
        showNavigation: Boolean,
        content: @Composable () -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (showNavigation) {
                            Modifier.consumeWindowInsets(NavigationBarDefaults.windowInsets)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content()
            }
            AnimatedVisibility(visible = showNavigation) {
                ShortNavigationBar {
                    items.forEach { item ->
                        ShortNavigationBarItem(
                            selected = item.selected,
                            onClick = item.onClick,
                            icon = {
                                MaterialIcon(
                                    imageVector = vectorResource(item.icon),
                                    contentDescription = stringResource(item.label),
                                )
                            },
                            label = { MaterialText(stringResource(item.label)) },
                        )
                    }
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
            tint = tint ?: androidx.compose.material3.LocalContentColor.current,
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
            tint = tint ?: androidx.compose.material3.LocalContentColor.current,
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
            tint = tint ?: androidx.compose.material3.LocalContentColor.current,
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
        MaterialButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape ?: MaterialButtonDefaults.shape,
            colors = materialButtonColors(containerColor, contentColor),
            border = border,
            contentPadding = contentPadding ?: MaterialButtonDefaults.ContentPadding,
            content = content,
        )
    }

    @Composable
    override fun Checkbox(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
    ) {
        MaterialCheckbox(
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
        MaterialSwitch(
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
        MaterialRadioButton(
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
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            content = content,
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
        MaterialCard(
            modifier = modifier,
            shape = materialCardShape(shape, elevated),
            colors = materialCardColors(color, contentColor, elevated),
            elevation = materialCardElevation(tonalElevation, elevated),
            border = border,
            content = content,
        )
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
        MaterialCard(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = materialCardShape(shape, elevated),
            colors = materialCardColors(color, contentColor, elevated),
            elevation = materialCardElevation(tonalElevation, elevated),
            border = border,
            content = content,
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
        MaterialSurface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            tonalElevation = tonalElevation.takeIf { it != Dp.Unspecified } ?: 0.dp,
            shadowElevation = shadowElevation.takeIf { it != Dp.Unspecified } ?: 0.dp,
            border = border,
            content = content,
        )
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
        MaterialSurface(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            color = color,
            contentColor = contentColor,
            tonalElevation = tonalElevation.takeIf { it != Dp.Unspecified } ?: 0.dp,
            shadowElevation = shadowElevation.takeIf { it != Dp.Unspecified } ?: 0.dp,
            border = border,
            content = content,
        )
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
            content()
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
            content()
        }
    }

    @Composable
    private fun materialButtonColors(
        containerColor: Color,
        contentColor: Color,
    ) = if (containerColor == Color.Unspecified && contentColor == Color.Unspecified) {
        MaterialButtonDefaults.buttonColors()
    } else {
        MaterialButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        )
    }

    @Composable
    private fun materialCardShape(
        shape: Shape?,
        elevated: Boolean,
    ) = when {
        shape != null -> shape
        elevated -> MaterialCardDefaults.elevatedShape
        else -> MaterialCardDefaults.shape
    }

    @Composable
    private fun materialCardColors(
        color: Color,
        contentColor: Color,
        elevated: Boolean,
    ): androidx.compose.material3.CardColors {
        val resolvedContentColor = if (color != Color.Unspecified && contentColor == Color.Unspecified) {
            contentColorFor(color)
        } else {
            contentColor
        }
        return if (elevated) {
            MaterialCardDefaults.elevatedCardColors(
                containerColor = color,
                contentColor = resolvedContentColor,
            )
        } else {
            MaterialCardDefaults.cardColors(
                containerColor = color,
                contentColor = resolvedContentColor,
            )
        }
    }

    @Composable
    private fun materialCardElevation(
        tonalElevation: Dp,
        elevated: Boolean,
    ) = if (tonalElevation == Dp.Unspecified) {
        if (elevated) {
            MaterialCardDefaults.elevatedCardElevation()
        } else {
            MaterialCardDefaults.cardElevation()
        }
    } else {
        if (elevated) {
            MaterialCardDefaults.elevatedCardElevation(defaultElevation = tonalElevation)
        } else {
            MaterialCardDefaults.cardElevation(defaultElevation = tonalElevation)
        }
    }
}

internal fun standardPlatformMaterialApi(): PlatformMaterialApi = MaterialPlatformMaterialApi

@Composable
private fun currentPlatformMaterialApi(): PlatformMaterialApi {
    if (GlobalContext.getOrNull() == null) return MaterialPlatformMaterialApi
    return koinInject()
}

@Immutable
class ButtonColors internal constructor(
    internal val containerColor: Color,
    internal val contentColor: Color,
)

object ButtonDefaults {
    @get:Composable
    val shape: Shape
        get() = MaterialButtonDefaults.shape

    val ContentPadding: PaddingValues
        get() = MaterialButtonDefaults.ContentPadding

    fun buttonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
    ) = ButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

@Immutable
class CardColors internal constructor(
    internal val containerColor: Color,
    internal val contentColor: Color,
    internal val elevated: Boolean,
)

@Immutable
class CardElevation internal constructor(
    internal val tonalElevation: Dp,
    internal val elevated: Boolean,
)

object CardDefaults {
    @get:Composable
    val shape: Shape
        get() = MaterialCardDefaults.shape

    @get:Composable
    val elevatedShape: Shape
        get() = MaterialCardDefaults.elevatedShape

    @Composable
    fun cardColors() = CardColors(
        containerColor = Color.Unspecified,
        contentColor = Color.Unspecified,
        elevated = false,
    )

    @Composable
    fun cardColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = contentColorFor(containerColor),
    ) = CardColors(
        containerColor = containerColor,
        contentColor = contentColor,
        elevated = false,
    )

    @Composable
    fun elevatedCardColors() = CardColors(
        containerColor = Color.Unspecified,
        contentColor = Color.Unspecified,
        elevated = true,
    )

    @Composable
    fun elevatedCardColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = contentColorFor(containerColor),
    ) = CardColors(
        containerColor = containerColor,
        contentColor = contentColor,
        elevated = true,
    )

    fun cardElevation(
        defaultElevation: Dp = Dp.Unspecified,
    ) = CardElevation(
        tonalElevation = defaultElevation,
        elevated = false,
    )

    fun elevatedCardElevation(
        defaultElevation: Dp = Dp.Unspecified,
    ) = CardElevation(
        tonalElevation = defaultElevation,
        elevated = true,
    )
}

@Composable
fun NavigationSuite(
    items: ImmutableList<NavigationSuiteItem>,
    showNavigation: Boolean,
    content: @Composable () -> Unit,
) {
    currentPlatformMaterialApi().NavigationSuite(
        items = items,
        showNavigation = showNavigation,
        content = content,
    )
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle? = null,
) {
    currentPlatformMaterialApi().Text(
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
        style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle? = null,
) {
    currentPlatformMaterialApi().Text(
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
        style = style,
    )
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    currentPlatformMaterialApi().Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    currentPlatformMaterialApi().Icon(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    currentPlatformMaterialApi().Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors? = null,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedColors = colors ?: ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    currentPlatformMaterialApi().Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = resolvedColors.containerColor,
        contentColor = resolvedColors.contentColor,
        border = border,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    currentPlatformMaterialApi().Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null,
) {
    currentPlatformMaterialApi().Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = thumbContent,
    )
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    currentPlatformMaterialApi().RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: MaterialIconButtonColors? = null,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val resolvedContainerColor = colors?.containerColor ?: containerColor
    val resolvedContentColor = colors?.contentColor ?: contentColor
    currentPlatformMaterialApi().IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = resolvedContainerColor,
        contentColor = resolvedContentColor,
        content = content,
    )
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    shape: Shape? = null,
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    tonalElevation: Dp = Dp.Unspecified,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedColors = colors ?: if (elevated) {
        if (color == Color.Unspecified && contentColor == Color.Unspecified) {
            CardDefaults.elevatedCardColors()
        } else {
            CardDefaults.elevatedCardColors(
                containerColor = color,
                contentColor = contentColor,
            )
        }
    } else {
        if (color == Color.Unspecified && contentColor == Color.Unspecified) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = color,
                contentColor = contentColor,
            )
        }
    }
    val resolvedElevation = elevation ?: if (elevated) {
        CardDefaults.elevatedCardElevation(defaultElevation = tonalElevation)
    } else {
        CardDefaults.cardElevation(defaultElevation = tonalElevation)
    }
    val resolvedElevated = elevation?.elevated ?: colors?.elevated ?: elevated
    currentPlatformMaterialApi().Card(
        modifier = modifier,
        elevated = resolvedElevated,
        shape = shape,
        color = resolvedColors.containerColor,
        contentColor = resolvedColors.contentColor,
        tonalElevation = resolvedElevation.tonalElevation,
        border = border,
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevated: Boolean = false,
    shape: Shape? = null,
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    tonalElevation: Dp = Dp.Unspecified,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedColors = colors ?: if (elevated) {
        if (color == Color.Unspecified && contentColor == Color.Unspecified) {
            CardDefaults.elevatedCardColors()
        } else {
            CardDefaults.elevatedCardColors(
                containerColor = color,
                contentColor = contentColor,
            )
        }
    } else {
        if (color == Color.Unspecified && contentColor == Color.Unspecified) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = color,
                contentColor = contentColor,
            )
        }
    }
    val resolvedElevation = elevation ?: if (elevated) {
        CardDefaults.elevatedCardElevation(defaultElevation = tonalElevation)
    } else {
        CardDefaults.cardElevation(defaultElevation = tonalElevation)
    }
    val resolvedElevated = elevation?.elevated ?: colors?.elevated ?: elevated
    currentPlatformMaterialApi().Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        elevated = resolvedElevated,
        shape = shape,
        color = resolvedColors.containerColor,
        contentColor = resolvedColors.contentColor,
        tonalElevation = resolvedElevation.tonalElevation,
        border = border,
        content = content,
    )
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = Dp.Unspecified,
    shadowElevation: Dp = Dp.Unspecified,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    currentPlatformMaterialApi().Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = Dp.Unspecified,
    shadowElevation: Dp = Dp.Unspecified,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    currentPlatformMaterialApi().Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}

@Composable
fun PrimaryTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTabRowDefaults.primaryContainerColor,
    content: @Composable () -> Unit,
) {
    currentPlatformMaterialApi().PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        content = content,
    )
}

@Composable
fun PrimaryScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTabRowDefaults.primaryContainerColor,
    edgePadding: Dp = MaterialTabRowDefaults.ScrollableTabRowEdgeStartPadding,
    content: @Composable () -> Unit,
) {
    currentPlatformMaterialApi().PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        edgePadding = edgePadding,
        content = content,
    )
}

@Composable
fun Tab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    LocalPlatformTabRowScope.current.Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        text = text,
        icon = icon,
    )
}
