package fr.husi.compose.material3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.WideLayoutMinWidth
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem as MaterialNavigationSuiteItem

@Immutable
class NavigationSuiteItem(
    val label: StringResource,
    val icon: DrawableResource,
    val selected: Boolean,
    val onClick: () -> Unit,
    val badgeCount: Int = 0,
)

private const val NAVIGATION_BADGE_MAX_COUNT = 99

/** Main navigation is always available on wide screens, including on pushed pages. */
@Composable
fun NavigationSuite(
    items: ImmutableList<NavigationSuiteItem>,
    isTopLevel: Boolean,
    snackbarHost: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationAvailable: Boolean = true,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val isWide = maxWidth >= WideLayoutMinWidth
        val navigationSuiteType = if (isWide) {
            NavigationSuiteType.WideNavigationRailCollapsed
        } else {
            NavigationSuiteType.ShortNavigationBarCompact
        }
        val showNavigation = navigationAvailable && (isTopLevel || isWide)
        val navigationState = rememberNavigationSuiteScaffoldState(
            initialValue = if (showNavigation) {
                NavigationSuiteScaffoldValue.Visible
            } else {
                NavigationSuiteScaffoldValue.Hidden
            },
        )
        LaunchedEffect(showNavigation) {
            if (showNavigation) navigationState.show() else navigationState.hide()
        }

        NavigationSuiteScaffold(
            modifier = Modifier.fillMaxSize(),
            navigationSuiteType = navigationSuiteType,
            state = navigationState,
            navigationItems = {
                if (showNavigation) {
                    if (isWide) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // WideNavigationRail normally imposes this minimum on direct children.
                            NavigationSuiteItems(
                                items,
                                navigationSuiteType,
                                itemModifier = Modifier.heightIn(min = 64.dp),
                            )
                        }
                    } else {
                        NavigationSuiteItems(items, navigationSuiteType)
                    }
                }
            },
        ) {
            val overlayInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
            )
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    Box(Modifier.windowInsetsPadding(overlayInsets)) { snackbarHost() }
                },
                floatingActionButton = {
                    Box(Modifier.windowInsetsPadding(overlayInsets)) { floatingActionButton() }
                },
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun NavigationSuiteItems(
    items: ImmutableList<NavigationSuiteItem>,
    navigationSuiteType: NavigationSuiteType,
    itemModifier: Modifier = Modifier,
) {
    items.forEach { item ->
        key(item.label) {
            MaterialNavigationSuiteItem(
                modifier = itemModifier,
                selected = item.selected,
                onClick = item.onClick,
                navigationSuiteType = navigationSuiteType,
                icon = {
                    MaterialIcon(vectorResource(item.icon), contentDescription = null)
                },
                label = { MaterialText(stringResource(item.label)) },
                badge = if (item.badgeCount > 0) {
                    {
                        Badge {
                            MaterialText(
                                if (item.badgeCount > NAVIGATION_BADGE_MAX_COUNT) {
                                    "$NAVIGATION_BADGE_MAX_COUNT+"
                                } else {
                                    item.badgeCount.toString()
                                },
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}
