package fr.husi.compose

import androidx.compose.material3.AppBarMenuState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.expand
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.navigate_next
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

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
                imageVector = vectorResource(Res.drawable.navigate_next),
                contentDescription = stringResource(Res.string.expand),
            )
        },
    )
}

@Composable
fun MoreOverIcon(menuState: AppBarMenuState) {
    SimpleIconButton(
        imageVector = vectorResource(Res.drawable.more_vert),
        contentDescription = stringResource(Res.string.more),
        onClick = menuState::show,
    )
}
