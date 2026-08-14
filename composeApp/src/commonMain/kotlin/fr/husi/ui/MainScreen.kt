@file:OptIn(KoinExperimentalAPI::class)

package fr.husi.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import fr.husi.bg.BackendState
import fr.husi.bg.Executable
import fr.husi.bg.ServiceAlert
import fr.husi.bg.ServiceState
import fr.husi.compose.BackHandler
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.NavigationSuite
import fr.husi.compose.material3.NavigationSuiteItem
import fr.husi.compose.material3.Text
import fr.husi.database.SagerDatabase
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.restartApplication
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.permission.AppPermission
import fr.husi.permission.LocalPermissionPlatform
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.access_local_network_denied
import fr.husi.resources.action_download
import fr.husi.resources.bug_report
import fr.husi.resources.cancel
import fr.husi.resources.description
import fr.husi.resources.directions
import fr.husi.resources.error
import fr.husi.resources.have_a_nice_day
import fr.husi.resources.location_permission_description
import fr.husi.resources.location_permission_title
import fr.husi.resources.menu_configuration
import fr.husi.resources.menu_dashboard
import fr.husi.resources.menu_log
import fr.husi.resources.menu_route
import fr.husi.resources.missing_plugin
import fr.husi.resources.no_thanks
import fr.husi.resources.auth_later_hint
import fr.husi.resources.ok
import fr.husi.resources.permission_denied
import fr.husi.resources.plugin_unknown
import fr.husi.resources.query_package_denied
import fr.husi.resources.question_mark
import fr.husi.resources.settings
import fr.husi.resources.transform
import fr.husi.resources.warning_amber
import fr.husi.results.LocalResultEventBus
import fr.husi.results.ResultEventBus
import fr.husi.ui.configuration.ProfileSelectSheet
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openconnect.OpenConnectAuthDialog
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject
import org.koin.compose.navigation3.EntryProvider
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.compose.scope.KoinScope
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    moveToBackground: () -> Unit,
    initialProcessText: String? = null,
) {
    val scopeId = remember {
        "main-screen:${Random.nextLong()}"
    }
    KoinScope<MainScreenScope>(scopeID = scopeId) {
        val mainScreenScope = currentKoinScope()
        val viewModel = koinViewModel<MainViewModel>()
        val entryProvider = koinEntryProvider<NavKey>(scope = mainScreenScope)
        MainScreenContent(
            modifier = modifier,
            viewModel = viewModel,
            moveToBackground = moveToBackground,
            initialProcessText = initialProcessText,
            koinScope = mainScreenScope,
            entryProvider = entryProvider,
        )
    }
}

@Composable
private fun MainScreenContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    moveToBackground: () -> Unit,
    initialProcessText: String?,
    koinScope: Scope,
    entryProvider: EntryProvider<NavKey>,
) {
    val permission = LocalPermissionPlatform.current
    val uriHandler = LocalUriHandler.current

    val savedStateConfiguration = remember { NavRoutes.savedStateConfiguration }
    val backStack = rememberNavBackStack(savedStateConfiguration, NavRoutes.Configuration)
    val resultBus = remember { ResultEventBus() }
    val navigator = remember(koinScope, backStack) {
        koinScope.get<Navigator> {
            parametersOf(backStack)
        }
    }
    val selectedTopLevelRoute = navigator.selectedTopLevelRoute
    val isAtStartDestination = navigator.isAtStartDestination
    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    val profilePickerController = remember(koinScope) {
        koinScope.get<ProfilePickerController>()
    }

    /**
     * Check query packages permission for rogue vendors.
     * If we don't query for `com.android.permission.GET_INSTALLED_APPS` permission,
     * only when we query all packages in foreground will pop the permission window for query permission.
     * @see <a href="https://www.taf.org.cn/upload/AssociationStandard/TTAF%20108-2022%20%E7%A7%BB%E5%8A%A8%E7%BB%88%E7%AB%AF%E5%BA%94%E7%94%A8%E8%BD%AF%E4%BB%B6%E5%88%97%E8%A1%A8%E6%9D%83%E9%99%90%E5%AE%9E%E6%96%BD%E6%8C%87%E5%8D%97.pdf">移动终端应用软件列表权限实施指南</a>
     */
    var showQueryPackageDeniedDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (permission.canRequestPermission(AppPermission.QueryInstalledApps) &&
            !permission.hasPermission(AppPermission.QueryInstalledApps)
        ) {
            permission.requestPermission(AppPermission.QueryInstalledApps) { granted ->
                if (granted) runOnDefaultDispatcher {
                    resolveRepository().stopService()
                    delay(500.milliseconds)
                    SagerDatabase.instance.close()
                    Executable.killAll(true)
                    restartApplication()
                } else {
                    showQueryPackageDeniedDialog = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasPostNotification =
            permission.hasPermission(AppPermission.PostNotifications)
        if (!hasPostNotification) {
            permission.requestPermission(AppPermission.PostNotifications)
        }
    }

    var showLocalNetworkDeniedDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (permission.canRequestPermission(AppPermission.LocalNetwork) &&
            !permission.hasPermission(AppPermission.LocalNetwork)
        ) {
            permission.requestPermission(AppPermission.LocalNetwork) { granted ->
                if (!granted) showLocalNetworkDeniedDialog = true
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            !isAtStartDestination -> {
                val popped = navigator.popBackStack()
                if (!popped) {
                    navigator.navigateToTopLevelRoute(NavRoutes.Configuration)
                }
            }

            else -> moveToBackground()
        }
    }

    LaunchedEffect(serviceStatus.state) {
        if (serviceStatus.state != ServiceState.Connected) {
            viewModel.resetUrlTestStatus()
        }
    }

    LaunchedEffect(initialProcessText) {
        if (!initialProcessText.isNullOrBlank()) {
            viewModel.parseProxy(initialProcessText)
        }
    }

    var showServiceAlert by remember { mutableStateOf<ServiceAlert?>(null) }

    LaunchedEffect(Unit) {
        BackendState.alerts.collect { alert ->
            when (alert) {
                is ServiceAlert.Common -> {
                    if (alert.message.isNotBlank()) {
                        viewModel.showSnackbar(StringOrRes.Direct(alert.message))
                    }
                }
                is ServiceAlert.MissingPlugin,
                is ServiceAlert.NeedWifiPermission,
                -> {
                    showServiceAlert = alert
                }
            }
        }
    }

    val topLevelDestinations = remember {
        persistentListOf(
            TopLevelDestination(
                Res.string.menu_configuration,
                Res.drawable.description,
                NavRoutes.Configuration,
            ),
            TopLevelDestination(
                Res.string.menu_dashboard,
                Res.drawable.transform,
                NavRoutes.Dashboard,
            ),
            TopLevelDestination(Res.string.menu_route, Res.drawable.directions, NavRoutes.Route),
            TopLevelDestination(Res.string.menu_log, Res.drawable.bug_report, NavRoutes.Log),
            TopLevelDestination(Res.string.settings, Res.drawable.settings, NavRoutes.Settings),
        )
    }
    val navigationItems = topLevelDestinations.map { destination ->
        val selected = selectedTopLevelRoute.matchesRoute(destination.route)
        NavigationSuiteItem(
            label = destination.label,
            icon = destination.icon,
            selected = selected,
            onClick = {
                if (!selected) {
                    navigator.navigateToTopLevelRoute(destination.route)
                }
            },
        )
    }.toPersistentList()

    NavigationSuite(
        items = navigationItems,
        showNavigation = navigator.isCurrentTopLevel,
    ) {
        CompositionLocalProvider(
            LocalResultEventBus provides resultBus,
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = navigator::popBackStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )

            profilePickerController.session?.let { session ->
                ProfileSelectSheet(
                    preSelected = session.preSelected,
                    onDismiss = profilePickerController::dismiss,
                    onSelected = profilePickerController::select,
                )
            }

            val openConnectController = koinInject<OpenConnectAuthController>()
            val pendingOpenConnectAuth by openConnectController.pendingDialogAuth
                .collectAsStateWithLifecycle()
            pendingOpenConnectAuth?.let { pending ->
                OpenConnectAuthDialog(
                    pending = pending,
                    controller = openConnectController,
                    showError = { message ->
                        viewModel.showSnackbar(StringOrRes.Direct(message))
                    },
                    onDismissed = {
                        viewModel.showSnackbar(
                            StringOrRes.Res(Res.string.auth_later_hint),
                        )
                    },
                )
            }
        }
    }

    if (showQueryPackageDeniedDialog) AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                permission.openPermissionSettings()
                showQueryPackageDeniedDialog = false
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.no_thanks)) {
                showQueryPackageDeniedDialog = false
                viewModel.showSnackbar(StringOrRes.Res(Res.string.have_a_nice_day))
            }
        },
        icon = {
            Icon(vectorResource(Res.drawable.warning_amber), null)
        },
        title = { Text(stringResource(Res.string.permission_denied)) },
        text = { Text(stringResource(Res.string.query_package_denied)) },
    )

    if (showLocalNetworkDeniedDialog) AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showLocalNetworkDeniedDialog = false
                permission.requestPermission(AppPermission.LocalNetwork) { granted ->
                    if (!granted) showLocalNetworkDeniedDialog = true
                }
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.no_thanks)) {
                showLocalNetworkDeniedDialog = false
            }
        },
        icon = {
            Icon(vectorResource(Res.drawable.warning_amber), null)
        },
        title = { Text(stringResource(Res.string.permission_denied)) },
        text = { Text(stringResource(Res.string.access_local_network_denied)) },
    )

    if (showServiceAlert != null) {
        when (val alert = showServiceAlert!!) {
            is ServiceAlert.MissingPlugin -> {
                val pluginName = alert.pluginName
                val plugin = PluginEntry.find(pluginName)
                if (plugin == null) {
                    showServiceAlert = null
                    viewModel.showSnackbar(
                        StringOrRes.ResWithParams(Res.string.plugin_unknown, pluginName),
                    )
                } else {
                    AlertDialog(
                        onDismissRequest = { showServiceAlert = null },
                        confirmButton = {
                            TextButton(stringResource(Res.string.action_download)) {
                                showServiceAlert = null
                                uriHandler.openUri(
                                    if (PlatformInfo.isAndroid) {
                                        plugin.downloadSource.apk
                                    } else {
                                        plugin.downloadSource.binary
                                    },
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(stringResource(Res.string.cancel)) {
                                showServiceAlert = null
                            }
                        },
                        icon = { Icon(vectorResource(Res.drawable.error), null) },
                        title = { Text(stringResource(plugin.displayName)) },
                        text = { Text(stringResource(Res.string.missing_plugin)) },
                    )
                }
            }

            is ServiceAlert.NeedWifiPermission -> {
                AlertDialog(
                    onDismissRequest = { showServiceAlert = null },
                    confirmButton = {
                        TextButton(stringResource(Res.string.ok)) {
                            showServiceAlert = null
                            permission.requestPermission(AppPermission.WifiInfo)
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(Res.string.no_thanks)) {
                            showServiceAlert = null
                        }
                    },
                    icon = { Icon(vectorResource(Res.drawable.warning_amber), null) },
                    title = { Text(stringResource(Res.string.location_permission_title)) },
                    text = { Text(stringResource(Res.string.location_permission_description)) },
                )
            }

            is ServiceAlert.Common -> Unit
        }
    }

}

@Immutable
private data class TopLevelDestination(
    val label: StringResource,
    val icon: DrawableResource,
    val route: NavRoutes,
)

private fun NavRoutes?.matchesRoute(
    route: NavRoutes,
): Boolean {
    val current = this ?: return false
    return current::class == route::class
}

@Composable
fun MainViewModelAlertDialog(
    dialog: MainViewModelUiEvent.AlertDialog,
    onConsumed: () -> Unit,
) {
    ScrollableDialog(
        onDismissRequest = {
            dialog.onDismiss?.invoke()
            onConsumed()
        },
        confirmButton = {
            TextButton(stringOrRes(dialog.confirmButton.label)) {
                dialog.confirmButton.onClick()
                onConsumed()
            }
        },
        dismissButton = dialog.dismissButton?.let { button ->
            {
                TextButton(stringOrRes(button.label)) {
                    button.onClick()
                    onConsumed()
                }
            }
        },
        icon = {
            Icon(
                vectorResource(
                    if (dialog.dismissButton != null) {
                        Res.drawable.question_mark
                    } else {
                        Res.drawable.error
                    },
                ),
                null,
            )
        },
        title = { Text(stringOrRes(dialog.title)) },
        text = { Text(stringOrRes(dialog.message)) },
    )
}
