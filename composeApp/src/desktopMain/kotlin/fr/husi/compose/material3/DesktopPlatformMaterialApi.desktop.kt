package fr.husi.compose.material3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal object DesktopPlatformMaterialApi : PlatformMaterialApi by standardPlatformMaterialApi() {
    @Composable
    override fun NavigationSuite(
        items: ImmutableList<NavigationSuiteItem>,
        showNavigation: Boolean,
        content: @Composable () -> Unit,
    ) {
        // Desktop windows have enough space that collapsing the rail is not worth a toggle.
        val railState = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)
        Row(modifier = Modifier.fillMaxSize()) {
            WideNavigationRail(
                state = railState,
                arrangement = Arrangement.Top,
            ) {
                items.forEach { item ->
                    WideNavigationRailItem(
                        selected = item.selected,
                        onClick = item.onClick,
                        icon = {
                            Icon(
                                imageVector = vectorResource(item.icon),
                                contentDescription = stringResource(item.label),
                            )
                        },
                        label = { Text(stringResource(item.label)) },
                        railExpanded = true,
                    )
                }
            }
            VerticalDivider(Modifier.fillMaxHeight())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                content()
            }
        }
    }
}
