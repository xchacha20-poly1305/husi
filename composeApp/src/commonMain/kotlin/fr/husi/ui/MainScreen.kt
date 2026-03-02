package fr.husi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.husi.AlertType
import fr.husi.bg.Alert
import fr.husi.bg.BackendState
import fr.husi.bg.Executable
import fr.husi.bg.ServiceState
import fr.husi.compose.BackHandler
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.DrawerCompat
import fr.husi.compose.TextButton
import fr.husi.compose.drawerIsCollapsible
import fr.husi.database.SagerDatabase
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.restartApplication
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.permission.AppPermission
import fr.husi.permission.LocalPermissionPlatform
import fr.husi.repository.repo
import fr.husi.resources.Res
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
import fr.husi.ui.configuration.ConfigurationScreen
import fr.husi.ui.configuration.ProfileSelectSheet
import fr.husi.ui.dashboard.ConnectionDetailScreen
import fr.husi.ui.dashboard.DashboardScreen
import fr.husi.ui.profile.ConfigEditScreen
import fr.husi.ui.profile.ProfileEditorScreen
import fr.husi.ui.tools.GetCertScreen
import fr.husi.ui.tools.RuleSetMatchScreen
import fr.husi.ui.tools.SpeedtestScreen
import fr.husi.ui.tools.StunScreen
import fr.husi.ui.tools.ToolsScreen
import fr.husi.ui.tools.VPNScannerScreen
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    moveToBackground: () -> Unit,
) {
    val permission = LocalPermissionPlatform.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    var profileEditorResultCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var appListSession by remember { mutableStateOf<AppListSession?>(null) }
    var configEditorSession by remember { mutableStateOf<ConfigEditorSession?>(null) }
    var assetEditResultCallback by remember { mutableStateOf<((AssetEditResult) -> Unit)?>(null) }
    var routeSettingsDraft by remember { mutableStateOf<RouteSettingsUiState?>(null) }
    var routeSettingsSavedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var profileSelectSession by remember { mutableStateOf<ProfileSelectSession?>(null) }
    val canCollapseDrawer = drawerIsCollapsible()
    val drawerState = rememberDrawerState(
        if (canCollapseDrawer) {
            DrawerValue.Closed
        } else {
            DrawerValue.Open
        },
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isAtStartDestination = currentDestination?.id == navController.graph.startDestinationId
    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    fun openProfileSelect(preSelected: Long?, onSelected: (Long) -> Unit) {
        profileSelectSession = ProfileSelectSession(preSelected, onSelected)
    }

    fun closeDrawer() {
        if (canCollapseDrawer) {
            scope.launch { drawerState.close() }
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
                    repo.stopService()
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

    BackHandler(enabled = true) {
        when {
            canCollapseDrawer && drawerState.isOpen -> scope.launch { drawerState.close() }

            !isAtStartDestination -> {
                val popped = navController.popBackStack()
                if (!popped) {
                    navController.navigate(NavRoutes.Configuration) {
                        launchSingleTop = true
                    }
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

    DrawerCompat(
        drawerState = drawerState,
        drawerContent = {
            @Composable
            fun BuildDrawerItem(info: DrawerItemInfo) {
                DrawerItem(
                    info = info,
                    navController = navController,
                    closeDrawer = ::closeDrawer,
                    currentDestination = currentDestination,
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
            NavigationDrawerItem(
                label = { Text(stringResource(Res.string.document)) },
                selected = false,
                onClick = {
                    closeDrawer()
                    uriHandler.openUri("https://codeberg.org/xchacha20-poly1305/husi/wiki")
                },
                modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
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
            if (canCollapseDrawer) {
                HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(NavigationDrawerItemDefaults.ItemPadding),
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
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Configuration,
        ) {
            fun onDrawerClick() {
                if (!canCollapseDrawer) {
                    return
                }
                scope.launch {
                    if (drawerState.isOpen) {
                        drawerState.close()
                    } else {
                        drawerState.open()
                    }
                }
            }
            composable<NavRoutes.Configuration> {
                ConfigurationScreen(
                    mainViewModel = viewModel,
                    onNavigationClick = ::onDrawerClick,
                    selectCallback = null,
                    preSelected = null,
                    openProfileEditor = { type, id, isSubscription, onResult ->
                        profileEditorResultCallback = onResult
                        navController.navigate(
                            NavRoutes.ProfileEditor(
                                type = type,
                                id = id,
                                subscription = isSubscription,
                            ),
                        )
                    },
                )
            }
            composable<NavRoutes.Groups> {
                GroupScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    openGroupSettings = { groupId ->
                        navController.navigate(NavRoutes.GroupSettings(groupId = groupId))
                    },
                )
            }
            composable<NavRoutes.Route> {
                RouteScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    openRouteSettings = { routeId ->
                        routeSettingsDraft = null
                        routeSettingsSavedCallback = null
                        navController.navigate(NavRoutes.RouteSettings(routeId = routeId))
                    },
                    openAssets = {
                        navController.navigate(NavRoutes.Assets)
                    },
                )
            }
            composable<NavRoutes.Settings> {
                SettingsScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    openAppManager = { navController.navigate(NavRoutes.AppManager) },
                )
            }
            composable<NavRoutes.Plugin> {
                PluginScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                )
            }
            composable<NavRoutes.Log> {
                LogcatScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                )
            }
            composable<NavRoutes.Dashboard> {
                DashboardScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    openConnectionDetail = { uuid ->
                        navController.navigate(NavRoutes.ConnectionsDetail(uuid = uuid))
                    },
                )
            }
            composable<NavRoutes.ConnectionsDetail> { entry ->
                val route = entry.toRoute<NavRoutes.ConnectionsDetail>()
                ConnectionDetailScreen(
                    uuid = route.uuid,
                    popup = { navController.navigateUp() },
                    navigateToRoutes = { navController.navigate(NavRoutes.Route) },
                    openRouteSettings = { initialState, onSaved ->
                        routeSettingsDraft = initialState
                        routeSettingsSavedCallback = onSaved
                        navController.navigate(
                            NavRoutes.RouteSettings(
                                routeId = -1L,
                                useDraft = true,
                            ),
                        )
                    },
                )
            }
            composable<NavRoutes.ProfileEditor> { entry ->
                val route = entry.toRoute<NavRoutes.ProfileEditor>()
                ProfileEditorScreen(
                    type = route.type,
                    profileId = route.id,
                    isSubscription = route.subscription,
                    onOpenProfileSelect = ::openProfileSelect,
                    onOpenConfigEditor = { initialText, onResult ->
                        configEditorSession = ConfigEditorSession(
                            initialText = initialText,
                            onResult = onResult,
                        )
                        navController.navigate(NavRoutes.ConfigEditor)
                    },
                    onResult = { updated ->
                        profileEditorResultCallback?.invoke(updated)
                        profileEditorResultCallback = null
                        navController.navigateUp()
                    },
                )
            }
            composable<NavRoutes.AppManager> {
                AppManagerScreen(
                    onBackPress = { navController.navigateUp() },
                )
            }
            composable<NavRoutes.GroupSettings> { entry ->
                val route = entry.toRoute<NavRoutes.GroupSettings>()
                GroupSettingsScreen(
                    groupId = route.groupId,
                    onBackPress = { navController.navigateUp() },
                    onOpenProfileSelect = ::openProfileSelect,
                )
            }
            composable<NavRoutes.RouteSettings> { entry ->
                val route = entry.toRoute<NavRoutes.RouteSettings>()
                val initialState = if (route.useDraft) routeSettingsDraft else null
                RouteSettingsScreen(
                    routeId = route.routeId,
                    initialState = initialState,
                    onBackPress = {
                        routeSettingsDraft = null
                        routeSettingsSavedCallback = null
                        navController.navigateUp()
                    },
                    onSaved = {
                        routeSettingsSavedCallback?.invoke()
                        routeSettingsDraft = null
                        routeSettingsSavedCallback = null
                        navController.navigateUp()
                    },
                    onOpenProfileSelect = ::openProfileSelect,
                    onOpenAppList = { initialPackages, onResult ->
                        appListSession = AppListSession(
                            initialPackages = initialPackages,
                            onResult = onResult,
                        )
                        navController.navigate(NavRoutes.AppList)
                    },
                    onOpenConfigEditor = { initialText, onResult ->
                        configEditorSession = ConfigEditorSession(
                            initialText = initialText,
                            onResult = onResult,
                        )
                        navController.navigate(NavRoutes.ConfigEditor)
                    },
                )
            }
            composable<NavRoutes.AppList> {
                val session = appListSession
                if (session == null) {
                    LaunchedEffect(Unit) {
                        navController.navigateUp()
                    }
                } else {
                    DisposableEffect(Unit) {
                        onDispose {
                            appListSession = null
                        }
                    }
                    AppListScreen(
                        initialPackages = session.initialPackages,
                        onSave = { selectedPackages ->
                            session.onResult(selectedPackages)
                            navController.navigateUp()
                        },
                    )
                }
            }
            composable<NavRoutes.ConfigEditor> {
                val session = configEditorSession
                if (session == null) {
                    LaunchedEffect(Unit) {
                        navController.navigateUp()
                    }
                } else {
                    ConfigEditScreen(
                        initialText = session.initialText,
                        back = {
                            configEditorSession = null
                            navController.navigateUp()
                        },
                        saveAndExit = { text ->
                            session.onResult(text)
                            configEditorSession = null
                            navController.navigateUp()
                        },
                    )
                }
            }
            composable<NavRoutes.Assets> {
                AssetsScreen(
                    onBackPress = { navController.navigateUp() },
                    onOpenAssetEditor = { assetName, onResult ->
                        assetEditResultCallback = onResult
                        navController.navigate(NavRoutes.AssetEdit(assetName = assetName))
                    },
                )
            }
            composable<NavRoutes.AssetEdit> { entry ->
                val route = entry.toRoute<NavRoutes.AssetEdit>()
                AssetEditScreen(
                    assetName = route.assetName,
                    onFinished = { result ->
                        assetEditResultCallback?.invoke(result)
                        assetEditResultCallback = null
                        navController.navigateUp()
                    },
                )
            }
            composable<NavRoutes.Tools> {
                ToolsScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    onOpenTool = { route ->
                        navController.navigate(route)
                    },
                )
            }
            composable<NavRoutes.ToolsPage.Stun> {
                StunScreen(
                    onBackPress = { navController.navigateUp() },
                )
            }
            composable<NavRoutes.ToolsPage.GetCert> {
                GetCertScreen(
                    onBack = { navController.navigateUp() },
                )
            }
            composable<NavRoutes.ToolsPage.VPNScanner> {
                VPNScannerScreen(
                    onBackPress = { navController.navigateUp() },
                )
            }
            composable<NavRoutes.ToolsPage.SpeedTest> {
                SpeedtestScreen(
                    onBackPress = { navController.navigateUp() },
                )
            }
            composable<NavRoutes.ToolsPage.RuleSetMatch> {
                RuleSetMatchScreen(
                    onBackPress = { navController.navigateUp() },
                )
            }
            composable<NavRoutes.About> {
                AboutScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    onNavigateToLibraries = { navController.navigate(NavRoutes.Libraries) },
                )
            }
            composable<NavRoutes.Libraries> {
                LibrariesScreen(
                    onBackPress = { navController.navigateUp() },
                )
            }
        }

        val session = profileSelectSession
        if (session != null) {
            ProfileSelectSheet(
                mainViewModel = viewModel,
                preSelected = session.preSelected,
                onDismiss = { profileSelectSession = null },
                onSelected = { id ->
                    session.onSelected(id)
                    profileSelectSession = null
                },
            )
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
                                uriHandler.openUri(plugin.downloadSource.apk)
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

    var showAlertDialog by remember { mutableStateOf<MainViewModelUiEvent.AlertDialog?>(null) }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.AlertDialog -> showAlertDialog = event

                else -> {}
            }
        }
    }
    if (showAlertDialog != null) {
        val dialog = showAlertDialog!!
        AlertDialog(
            onDismissRequest = { showAlertDialog = null },
            confirmButton = {
                TextButton(stringOrRes(dialog.confirmButton.label)) {
                    dialog.confirmButton.onClick()
                    showAlertDialog = null
                }
            },
            dismissButton = dialog.dismissButton?.let { button ->
                {
                    TextButton(stringOrRes(button.label)) {
                        button.onClick()
                        showAlertDialog = null
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
            text = {
                val scrollState = rememberScrollState()
                Row {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scrollState),
                    ) {
                        Text(stringOrRes(dialog.message))
                    }

                    BoxedVerticalScrollbar(
                        modifier = Modifier.fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState = scrollState),
                        style = defaultMaterialScrollbarStyle().copy(
                            thickness = 12.dp,
                        ),
                    )
                }
            },
        )
    }
}

@Immutable
private data class DrawerItemInfo(
    val label: StringResource,
    val icon: DrawableResource,
    val route: NavRoutes,
)

private data class AppListSession(
    val initialPackages: Set<String>,
    val onResult: (Set<String>) -> Unit,
)

private data class ConfigEditorSession(
    val initialText: String,
    val onResult: (String) -> Unit,
)

private data class ProfileSelectSession(
    val preSelected: Long?,
    val onSelected: (Long) -> Unit,
)

@Composable
private fun DrawerItem(
    modifier: Modifier = Modifier,
    info: DrawerItemInfo,
    navController: NavController,
    closeDrawer: () -> Unit,
    currentDestination: NavDestination?,
) {
    val selected = currentDestination.matchesRoute(info.route)
    NavigationDrawerItem(
        label = { Text(stringResource(info.label)) },
        selected = selected,
        onClick = {
            closeDrawer()
            if (!selected) {
                navController.navigate(info.route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        icon = {
            Icon(vectorResource(info.icon), null)
        },
    )
}

private fun NavDestination?.matchesRoute(
    route: NavRoutes,
): Boolean {
    val destination = this ?: return false
    return destination.hasRoute(route::class)
}
