@file:OptIn(KoinExperimentalAPI::class, ExperimentalWasmJsInterop::class)

package fr.husi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import fr.husi.ProvideAppLanguageEnvironment
import fr.husi.compose.material3.NavigationSuite
import fr.husi.compose.material3.NavigationSuiteItem
import fr.husi.compose.theme.AppTheme
import fr.husi.database.DataStore
import fr.husi.database.RemoteBackend
import fr.husi.installAppLanguageEnvironment
import fr.husi.ktx.Logs
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.clash_rules
import fr.husi.resources.description
import fr.husi.resources.layers
import fr.husi.resources.link
import fr.husi.resources.menu_log
import fr.husi.resources.proxy_set
import fr.husi.resources.settings
import fr.husi.resources.speed
import fr.husi.resources.traffic_connections
import fr.husi.resources.traffic_status
import io.github.xchacha20_poly1305.libghostty.GhosttyWebAssembly
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.compose.scope.UnboundKoinScope
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI

@Composable
internal fun WebApp() {
    val appTheme by DataStore.appTheme.flow().collectAsStateWithLifecycle(initialValue = null)
    val nightTheme by DataStore.nightTheme.flow().collectAsStateWithLifecycle(initialValue = null)
    val appLanguage by DataStore.appLanguage.flow().collectAsStateWithLifecycle(initialValue = null)
    val theme = appTheme ?: return
    val night = nightTheme ?: return
    val language = appLanguage ?: return
    val darkMode = when (night) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    SideEffect { installAppLanguageEnvironment { language } }
    ProvideAppLanguageEnvironment(language) {
        WebAppContent(theme, darkMode)
    }
}

@OptIn(KoinDelicateAPI::class)
@Composable
private fun WebAppContent(appTheme: Int, isDarkMode: Boolean) {
    val koin = getKoin()
    CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
        val scope = viewModel { MainScreenScopeHolder(koin) }.scope
        UnboundKoinScope(scope) {
            val state = koinInject<RemoteState>()
            if (!state.initialized) return@UnboundKoinScope
            val snackbar = koinInject<SnackbarEmitter>()
            val snackbarHost = remember { SnackbarHostState() }
            val backStack =
                rememberNavBackStack(RemoteRoute.savedStateConfiguration, RemoteRoute.Setup)
            val restored = remoteBackStack(backStack, state.selected != null)
            val routes = if (state.selected != null && restored.any { route ->
                    when (state.backend) {
                        RemoteBackend.CLASH -> !remoteRouteAvailable(route, true)
                        RemoteBackend.SING_BOX -> !remoteRouteAvailable(route, false)
                        else -> !remoteRouteAvailable(route, true) || !remoteRouteAvailable(route, false)
                    }
                }) {
                listOf(RemoteRoute.Stats)
            } else {
                restored
            }
            SideEffect {
                if (backStack != routes) {
                    backStack.clear()
                    backStack.addAll(routes)
                }
            }
            val navigator = remember(backStack) { Navigator(backStack, RemoteRoute.topLevel) }
            val entryProvider = koinEntryProvider<NavKey>(scope = scope)
            SnackbarEmitterEffect(snackbar, snackbarHost)
            LaunchedEffect(Unit) {
                removeLoadingIndicator()
                try {
                    GhosttyWebAssembly.load()
                    state.terminalReady = true
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        ensureActive()
                    }
                    Logs.w("Terminal WebAssembly failed to load", e)
                }
            }
            LaunchedEffect(state.error) {
                if (state.error.isNotEmpty()) {
                    snackbar.show(StringOrRes.Direct(state.error))
                }
            }
            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalSnackbarEmitter provides snackbar,
            ) {
                AppTheme(
                    appTheme = appTheme,
                    isDarkMode = isDarkMode,
                ) {
                    NavigationSuite(
                        items =
                            listOf(
                                Triple(
                                    RemoteRoute.Stats,
                                    Res.string.traffic_status,
                                    Res.drawable.speed,
                                ),
                                Triple(
                                    RemoteRoute.ProxySet,
                                    Res.string.proxy_set,
                                    Res.drawable.layers,
                                ),
                                Triple(
                                    RemoteRoute.Connections,
                                    Res.string.traffic_connections,
                                    Res.drawable.link,
                                ),
                                Triple(
                                    RemoteRoute.Logs,
                                    Res.string.menu_log,
                                    Res.drawable.description,
                                ),
                                Triple(RemoteRoute.Rules, Res.string.clash_rules, Res.drawable.description),
                                Triple(
                                    RemoteRoute.Settings,
                                    Res.string.settings,
                                    Res.drawable.settings,
                                ),
                            )
                                .filter { (route, _, _) ->
                                    remoteRouteAvailable(route, state.isClash)
                                }
                                .map { (route, label, icon) ->
                                    NavigationSuiteItem(
                                        label,
                                        icon,
                                        routes.lastOrNull { it in RemoteRoute.topLevel } == route,
                                        { navigator.navigateToTopLevelRoute(route) },
                                    )
                                }
                                .toPersistentList(),
                        isTopLevel = routes.last() in RemoteRoute.topLevel,
                        navigationAvailable = state.selected != null,
                        snackbarHost = { SnackbarHost(snackbarHost) },
                        floatingActionButton = {},
                    ) {
                        NavDisplay(
                            backStack = routes,
                            onBack = {
                                if (state.selected != null) {
                                    navigator.popBackStack()
                                }
                            },
                            entryDecorators =
                                listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                    rememberResultEventBusNavEntryDecorator(),
                                ),
                            entryProvider = entryProvider,
                        )
                    }
                }
            }
        }
    }
}

/** Removes the spinner `index.html` shows until the first composition. */
@JsFun("() => { document.getElementById('loading')?.remove(); }")
private external fun removeLoadingIndicator()
