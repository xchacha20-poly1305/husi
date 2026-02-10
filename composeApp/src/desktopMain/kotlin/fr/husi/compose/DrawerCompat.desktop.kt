package fr.husi.compose

import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

@Composable
internal actual fun DrawerCompat(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val drawerWidth = remember(windowInfo.containerSize.width, density) {
        with(density) {
            (windowInfo.containerSize.width.toDp() * 0.24f).coerceIn(220.dp, 320.dp)
        }
    }
    PermanentNavigationDrawer(
        drawerContent = {
            val colorOutline = MaterialTheme.colorScheme.outline
            PermanentDrawerSheet(
                modifier = Modifier
                    .width(drawerWidth)
                    .drawWithContent {
                        drawContent()
                        val strokeWidth = 2.dp.toPx()
                        val x = size.width - strokeWidth / 2
                        drawLine(
                            color = colorOutline,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = strokeWidth,
                        )
                    },
            ) {
                drawerContent()
            }
        },
        content = content,
    )
}

internal actual fun drawerIsCollapsible(): Boolean = false
