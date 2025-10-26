package io.nekohasekai.sagernet.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.nekohasekai.sagernet.R

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopAppBar(
    @StringRes title: Int,
    navigationIcon: ImageVector,
    navigationDescription: String,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavigationClick: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            SimpleIconButton(
                imageVector = navigationIcon,
                contentDescription = navigationDescription,
                onClick = onNavigationClick,
            )
        },
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
    )
}

@Preview
@Composable
private fun PreviewSimpleTopAppBar() {
    SimpleTopAppBar(
        title = R.string.app_name,
        navigationIcon = ImageVector.vectorResource(R.drawable.menu),
        navigationDescription = stringResource(R.string.menu),
    ) {}
}