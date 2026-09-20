@file:OptIn(KoinExperimentalAPI::class)

package fr.husi.di

import fr.husi.ui.AppListScreen
import fr.husi.ui.AppManagerScreen
import fr.husi.ui.LocalNavigator
import fr.husi.ui.MainScreenScope
import fr.husi.ui.NavRoutes
import fr.husi.ui.tools.VPNScannerScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

internal val androidNavigationModule = module {
    scope<MainScreenScope> {
        navigation<NavRoutes.AppManager> { _ ->
            val navigator = LocalNavigator.current
            AppManagerScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.AppList> { route ->
            val navigator = LocalNavigator.current
            AppListScreen(
                initialPackages = route.initialPackages,
                resultKey = route.resultKey,
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.VPNScanner> { _ ->
            val navigator = LocalNavigator.current
            VPNScannerScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }
    }
}
