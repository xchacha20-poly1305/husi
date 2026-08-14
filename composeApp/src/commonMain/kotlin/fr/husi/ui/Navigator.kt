package fr.husi.ui

import androidx.navigation3.runtime.NavKey

class Navigator(
    private val backStack: MutableList<NavKey>,
) {
    val currentRoute: NavRoutes?
        get() = backStack.lastOrNull() as? NavRoutes

    val selectedTopLevelRoute: NavRoutes?
        get() = backStack.lastOrNull {
            (it as? NavRoutes)?.isTopLevelRoute() == true
        } as? NavRoutes

    private fun NavRoutes.isTopLevelRoute(): Boolean {
        return when (this) {
            NavRoutes.Configuration,
            NavRoutes.Dashboard,
            NavRoutes.Route,
            NavRoutes.Log,
            NavRoutes.Settings,
                -> true

            else -> false
        }
    }

    val isCurrentTopLevel: Boolean
        get() = currentRoute?.isTopLevelRoute() == true

    val isAtStartDestination: Boolean
        get() = currentRoute == NavRoutes.Configuration

    fun popBackStack(): Boolean {
        if (backStack.size <= 1) {
            return false
        }
        backStack.removeLastOrNull()
        return true
    }

    fun navigateTo(route: NavRoutes) {
        backStack.add(route)
    }

    fun navigateToTopLevelRoute(route: NavRoutes) {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }
}
