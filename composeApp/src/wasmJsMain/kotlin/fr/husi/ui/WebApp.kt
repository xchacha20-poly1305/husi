@file:OptIn(KoinExperimentalAPI::class)

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
import fr.husi.bg.RemoteNotificationAction
import fr.husi.bg.RemoteNotificationWatcher
import fr.husi.compose.material3.NavigationSuite
import fr.husi.compose.material3.NavigationSuiteItem
import fr.husi.compose.theme.AppTheme
import fr.husi.database.DataStore
import fr.husi.database.RemoteBackend
import fr.husi.installAppLanguageEnvironment
import fr.husi.ktx.Logs
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.description
import fr.husi.resources.layers
import fr.husi.resources.link
import fr.husi.resources.menu_log
import fr.husi.resources.proxy_set
import fr.husi.resources.settings
import fr.husi.resources.speed
import fr.husi.resources.traffic_connections
import fr.husi.resources.traffic_status
import fr.husi.resources.web_auth_later_hint
import fr.husi.ui.dashboard.DashboardNoticeTracker
import fr.husi.ui.openconnect.OpenConnectAuthDialog
import fr.husi.ui.openvpn.OpenVPNAuthDialog
import fr.husi.ui.remote.LocalRemoteSetupLink
import fr.husi.ui.remote.readRemoteSetupLink
import io.github.xchacha20_poly1305.libghostty.GhosttyWebAssembly
import kotlinx.browser.document
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
    val setupLink = remember { readRemoteSetupLink() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
        val scope = viewModel { MainScreenScopeHolder(koin) }.scope
        UnboundKoinScope(scope) {
            val state = koinInject<RemoteState>()
            val watcher = koinInject<RemoteNotificationWatcher>()
            val dashboardNoticeTracker = koinInject<DashboardNoticeTracker>()
            if (!state.initialized) return@UnboundKoinScope
            LaunchedEffect(Unit) {
                val link = setupLink?.takeIf { it.addImmediately } ?: return@LaunchedEffect
                try {
                    val server = state.servers.firstOrNull {
                        it.url == link.server.url
                            && it.secret == link.server.secret
                            && it.backend == link.server.backend
                    } ?: state.save(link.server)
                    state.connect(server)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    state.reportCriticalError("Saving setup link server failed", failure)
                }
            }
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
            val unreadStatsNoticeCount by dashboardNoticeTracker.unreadCount.collectAsStateWithLifecycle()
            val isStatsSelected = routes.lastOrNull() == RemoteRoute.Stats
            SideEffect { watcher.currentRoute = routes.lastOrNull() }
            LaunchedEffect(isStatsSelected, unreadStatsNoticeCount) {
                if (isStatsSelected && unreadStatsNoticeCount > 0) {
                    dashboardNoticeTracker.markAllRead()
                }
            }
            LaunchedEffect(watcher, navigator, state) {
                watcher.clickActions.collect { action ->
                    if (state.selected == null || state.isClash) return@collect
                    when (action) {
                        is RemoteNotificationAction.OpenOpenConnectAuth -> {
                            state.openConnectActions.undismissDialog(action.endpointTag, action.challengeId)
                            navigator.navigateToTopLevelRoute(RemoteRoute.Stats)
                        }

                        is RemoteNotificationAction.OpenOpenVPNAuth -> {
                            state.openVPNActions.undismissDialog(action.endpointTag, action.challengeId)
                            navigator.navigateToTopLevelRoute(RemoteRoute.Stats)
                        }

                        is RemoteNotificationAction.OpenTailscaleEndpoint -> {
                            val target = RemoteRoute.Endpoint(action.endpointTag)
                            if (navigator.currentRoute != target) {
                                navigator.navigateTo(target)
                            }
                        }

                        is RemoteNotificationAction.OpenTaildropInbox -> {
                            val target = RemoteRoute.Inbox(action.endpointTag)
                            if (navigator.currentRoute != target) {
                                navigator.navigateTo(target)
                            }
                        }
                    }
                }
            }
            SnackbarEmitterEffect(snackbar, snackbarHost)
            LaunchedEffect(Unit) {
                document.getElementById("loading")?.remove()
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
                LocalRemoteSetupLink provides setupLink,
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
                                        badgeCount = if (route == RemoteRoute.Stats) unreadStatsNoticeCount else 0,
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

                        if (state.selected != null && !state.isClash) {
                            val pendingOpenConnectAuth by state.openConnectActions.pendingDialogAuth
                                .collectAsStateWithLifecycle()
                            pendingOpenConnectAuth?.let { pending ->
                                OpenConnectAuthDialog(
                                    pending = pending,
                                    actions = state.openConnectActions,
                                    onDismiss = {
                                        state.openConnectActions.dismissDialog(
                                            pending.endpointTag,
                                            pending.challenge.id,
                                        )
                                        snackbar.show(StringOrRes.Res(Res.string.web_auth_later_hint))
                                    },
                                )
                            }

                            val pendingOpenVPNAuth by state.openVPNActions.pendingDialogAuth
                                .collectAsStateWithLifecycle()
                            pendingOpenVPNAuth?.let { pending ->
                                OpenVPNAuthDialog(
                                    pending = pending,
                                    actions = state.openVPNActions,
                                    onDismiss = {
                                        state.openVPNActions.dismissDialog(
                                            pending.endpointTag,
                                            pending.challenge.id,
                                        )
                                        snackbar.show(StringOrRes.Res(Res.string.web_auth_later_hint))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
