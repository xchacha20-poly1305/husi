@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.compose

import androidx.compose.material3.AppBarMenuState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.nekohasekai.sagernet.R

@Composable
fun ExpandableDropdownMenuItem(
    text: String,
    shape: Shape = MenuDefaults.shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(text) },
        shape = shape,
        modifier = modifier,
        trailingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.navigate_next),
                contentDescription = stringResource(R.string.expand),
            )
        },
    )
}

@Composable
fun MoreOverIcon(menuState: AppBarMenuState) {
    SimpleIconButton(
        imageVector = ImageVector.vectorResource(R.drawable.more_vert),
        contentDescription = stringResource(R.string.more),
        onClick = menuState::show,
    )
}