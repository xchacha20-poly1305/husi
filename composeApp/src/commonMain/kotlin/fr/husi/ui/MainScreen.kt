@file:OptIn(KoinExperimentalAPI::class)

package fr.husi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import fr.husi.AlertType
import fr.husi.bg.Alert
import fr.husi.bg.BackendState
import fr.husi.bg.Executable
import fr.husi.bg.ServiceState
import fr.husi.compose.BackHandler
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.TextButton
import fr.husi.compose.material3.DrawerItem
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.IconButton
import fr.husi.compose.material3.NavigationDrawer
import fr.husi.compose.material3.Text
import fr.husi.compose.material3.rememberDrawerStateHolder
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
import fr.husi.resources.close
import fr.husi.resources.construction
import fr.husi.resources.data_usage
import fr.husi.resources.description
import fr.husi.resources.directions
import fr.husi.resources.document
import fr.husi.resources.error
import fr.husi.resources.fast_rewind
import fr.husi.resources.have_a_nice_day
import fr.husi.resources.info
import fr.husi.resources.location_permission_description
import fr.husi.resources.location_permission_title
import fr.husi.resources.menu_about
import fr.husi.resources.menu_configuration
import fr.husi.resources.menu_dashboard
import fr.husi.resources.menu_group
import fr.husi.resources.menu_log
import fr.husi.resources.menu_route
import fr.husi.resources.menu_tools
import fr.husi.resources.missing_plugin
import fr.husi.resources.nfc
import fr.husi.resources.no_thanks
import fr.husi.resources.ok
import fr.husi.resources.permission_denied
import fr.husi.resources.plugin
import fr.husi.resources.plugin_unknown
import fr.husi.resources.query_package_denied
import fr.husi.resources.question_mark
import fr.husi.resources.settings
import fr.husi.resources.transform
import fr.husi.resources.view_list
import fr.husi.resources.warning_amber
import fr.husi.results.LocalResultEventBus
import fr.husi.results.ResultEventBus
import fr.husi.ui.configuration.ProfileSelectSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.currentKoinScope
import org.koin.compose.navigation3.EntryProvider
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.compose.scope.KoinScope
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope
import kotlin.random.Random

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
    val scope = rememberCoroutineScope()

    val savedStateConfiguration = remember { NavRoutes.savedStateConfiguration }
    val backStack = rememberNavBackStack(savedStateConfiguration, NavRoutes.Configuration)
    val resultBus = remember { ResultEventBus() }
    val drawerStateHolder = rememberDrawerStateHolder()
    val navigator = remember(koinScope, backStack) {
        koinScope.get<Navigator> {
            parametersOf(backStack)
        }
    }
    val selectedDrawerRoute = navigator.selectedDrawerRoute
    val isAtStartDestination = navigator.isAtStartDestination
    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    val profilePickerController = remember(koinScope) {
        koinScope.get<ProfilePickerController>()
    }

    fun closeDrawer() {
        if (drawerStateHolder.canCollapse) {
            scope.launch { drawerStateHolder.close() }
        }
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
                    delay(500)
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
            drawerStateHolder.canCollapse && drawerStateHolder.isOpen -> scope.launch { drawerStateHolder.close() }

            !isAtStartDestination -> {
                val popped = navigator.popBackStack()
                if (!popped) {
                    navigator.navigateToDrawerRoute(NavRoutes.Configuration)
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

    var showServiceAlert by remember { mutableStateOf<Alert?>(null) }

    LaunchedEffect(Unit) {
        BackendState.alerts.collect { alert ->
            if (alert.type == AlertType.COMMON) {
                if (alert.message.isNotBlank()) {
                    viewModel.showSnackbar(StringOrRes.Direct(alert.message))
                }
            } else {
                showServiceAlert = alert
            }
        }
    }

    NavigationDrawer(
        drawerStateHolder = drawerStateHolder,
        drawerContent = {
            @Composable
            fun BuildDrawerItem(info: DrawerItemInfo) {
                DrawerItem(
                    info = info,
                    closeDrawer = ::closeDrawer,
                    selectedDrawerRoute = selectedDrawerRoute,
                    onNavigate = navigator::navigateToDrawerRoute,
                )
            }

            val dividerPadding = 4.dp
            val items0 = remember {
                persistentListOf(
                    DrawerItemInfo(
                        Res.string.menu_configuration,
                        Res.drawable.description,
                        NavRoutes.Configuration,
                    ),
                    DrawerItemInfo(
                        Res.string.menu_group,
                        Res.drawable.view_list,
                        NavRoutes.Groups,
                    ),
                    DrawerItemInfo(
                        Res.string.menu_route,
                        Res.drawable.directions,
                        NavRoutes.Route,
                    ),
                    DrawerItemInfo(
                        Res.string.settings,
                        Res.drawable.settings,
                        NavRoutes.Settings,
                    ),
                    DrawerItemInfo(
                        Res.string.plugin,
                        Res.drawable.nfc,
                        NavRoutes.Plugin,
                    ),
                )
            }
            for (info in items0) BuildDrawerItem(info)
            HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
            val items1 = remember {
                persistentListOf(
                    DrawerItemInfo(Res.string.menu_log, Res.drawable.bug_report, NavRoutes.Log),
                    DrawerItemInfo(
                        Res.string.menu_dashboard,
                        Res.drawable.transform,
                        NavRoutes.Dashboard,
                    ),
                    DrawerItemInfo(
                        Res.string.menu_tools,
                        Res.drawable.construction,
                        NavRoutes.Tools,
                    ),
                )
            }
            for (info in items1) BuildDrawerItem(info)
            HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
            DrawerItem(
                label = { Text(stringResource(Res.string.document)) },
                selected = false,
                onClick = {
                    closeDrawer()
                    uriHandler.openUri("https://codeberg.org/xchacha20-poly1305/husi/wiki")
                },
                icon = {
                    Icon(vectorResource(Res.drawable.data_usage), null)
                },
            )
            BuildDrawerItem(
                DrawerItemInfo(
                    Res.string.menu_about,
                    Res.drawable.info,
                    NavRoutes.About,
                ),
            )
            if (drawerStateHolder.canCollapse) {
                HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above,
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(Res.string.close))
                            }
                        },
                        state = tooltipState,
                    ) {
                        IconButton(
                            onClick = ::closeDrawer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.fast_rewind),
                                contentDescription = stringResource(Res.string.close),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        },
    ) {
        fun onDrawerClick() {
            if (!drawerStateHolder.canCollapse) {
                return
            }
            scope.launch {
                if (drawerStateHolder.isOpen) {
                    drawerStateHolder.close()
                } else {
                    drawerStateHolder.open()
                }
            }
        }

        remember(koinScope) {
            koinScope.get<DrawerController> {
                parametersOf(::onDrawerClick)
            }
        }

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
        val alert = showServiceAlert!!
        when (alert.type) {
            AlertType.MISSING_PLUGIN -> {
                val pluginName = alert.message
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

            AlertType.NEED_WIFI_PERMISSION -> {
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
        }
    }

}

@Immutable
private data class DrawerItemInfo(
    val label: StringResource,
    val icon: DrawableResource,
    val route: NavRoutes,
)

@Composable
private fun DrawerItem(
    modifier: Modifier = Modifier,
    info: DrawerItemInfo,
    closeDrawer: () -> Unit,
    selectedDrawerRoute: NavRoutes?,
    onNavigate: (NavRoutes) -> Unit,
) {
    val selected = selectedDrawerRoute.matchesRoute(info.route)
    DrawerItem(
        label = { Text(stringResource(info.label)) },
        selected = selected,
        onClick = {
            closeDrawer()
            if (!selected) {
                onNavigate(info.route)
            }
        },
        modifier = modifier,
        icon = {
            Icon(vectorResource(info.icon), null)
        },
    )
}

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