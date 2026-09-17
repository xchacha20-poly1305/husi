package fr.husi.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import dev.chrisbanes.haze.HazeState
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import fr.husi.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.resources.menu
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun SimpleIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TooltipIconButton(
        onClick = onClick,
        icon = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    val tooltipState = rememberTooltipState()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip {
                Text(contentDescription)
            }
        },
        state = tooltipState,
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}


@Composable
fun TextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text)
    }
}

@Composable
fun SimpleTopAppBar(
    hazeState: HazeState?,
    title: @Composable () -> Unit,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable CapsuleActionsScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CapsuleTopBar(
        hazeState = hazeState,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
    )
}

@Preview
@Composable
private fun PreviewSimpleTopAppBar() {
    SimpleTopAppBar(
        hazeState = null,
        title = {
            stringResource(Res.string.app_name)
        },
        navigationIcon = {
            SimpleIconButton(
                imageVector = vectorResource(Res.drawable.menu),
                contentDescription = stringResource(Res.string.menu),
                onClick = { },
            )
        },
    )
}
