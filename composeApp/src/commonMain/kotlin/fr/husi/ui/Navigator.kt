package fr.husi.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided")
}

class Navigator(
    private val backStack: MutableList<NavKey>,
    private val topLevelRoutes: Set<NavKey>,
) {
    val currentRoute: NavKey?
        get() = backStack.lastOrNull()

    val selectedTopLevelRoute: NavKey?
        get() = backStack.lastOrNull { it in topLevelRoutes }

    val isCurrentTopLevel: Boolean
        get() = currentRoute in topLevelRoutes

    fun popBackStack(): Boolean {
        if (backStack.size <= 1) {
            return false
        }
        backStack.removeLastOrNull()
        return true
    }

    fun navigateTo(route: NavKey) {
        backStack.add(route)
    }

    fun navigateToTopLevelRoute(route: NavKey) {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }
}
