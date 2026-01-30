package io.nekohasekai.sagernet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.AlertType
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.NavRoutes
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.Alert
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.ktx.hasPermission
import io.nekohasekai.sagernet.ktx.openPermissionSettings
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.configuration.ConfigurationScreen
import io.nekohasekai.sagernet.ui.dashboard.ConnectionDetailScreen
import io.nekohasekai.sagernet.ui.dashboard.DashboardScreen
import io.nekohasekai.sagernet.ui.tools.ToolsScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PERMISSION_GET_APPS = "com.android.permission.GET_INSTALLED_APPS"

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    connection: SagerConnection,
    exit: () -> Unit,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val serviceStatus by connection.status.collectAsStateWithLifecycle()

    /**
     * Check query packages permission for rogue vendors.
     * If we don't query for `com.android.permission.GET_INSTALLED_APPS` permission,
     * only when we query all packages in foreground will pop the permission window for query permission.
     * @see <a href="https://www.taf.org.cn/upload/AssociationStandard/TTAF%20108-2022%20%E7%A7%BB%E5%8A%A8%E7%BB%88%E7%AB%AF%E5%BA%94%E7%94%A8%E8%BD%AF%E4%BB%B6%E5%88%97%E8%A1%A8%E6%9D%83%E9%99%90%E5%AE%9E%E6%96%BD%E6%8C%87%E5%8D%97.pdf">移动终端应用软件列表权限实施指南</a>
     */
    var showQueryPackageDeniedDialog by remember { mutableStateOf(false) }
    val packagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) runOnDefaultDispatcher {
            repo.stopService()
            delay(500)
            SagerDatabase.instance.close()
            Executable.killAll(true)
            ProcessPhoenix.triggerRebirth(context, Intent(context, MainActivity::class.java))
        } else {
            showQueryPackageDeniedDialog = true
        }
    }
    LaunchedEffect(Unit) {
        val hasVendorRuntime = try {
            context.packageManager.getPermissionInfo(PERMISSION_GET_APPS, 0) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (hasVendorRuntime && !context.hasPermission(PERMISSION_GET_APPS)) {
            packagePermissionLauncher.launch(PERMISSION_GET_APPS)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val postNotificationLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            val hasPostNotification = context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (!hasPostNotification) {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var showLicenseDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!DataStore.acceptedLicense) showLicenseDialog = true
    }

    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            currentRoute != NavRoutes.CONFIGURATION -> navController.navigateUp()
            else -> activity?.moveTaskToBack(true)
        }
    }

    LaunchedEffect(serviceStatus.state) {
        if (serviceStatus.state != BaseService.State.Connected) {
            viewModel.resetUrlTestStatus()
        }
    }

    var showServiceAlert by remember { mutableStateOf<Alert?>(null) }
    var showBackgroundLocationDialog by remember { mutableStateOf(false) }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val finePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showBackgroundLocationDialog = true
        }
    }

    LaunchedEffect(Unit) {
        connection.alert.collect { alert ->
            if (alert.type == AlertType.COMMON) {
                if (alert.message.isNotBlank()) {
                    viewModel.showSnackbar(StringOrRes.Direct(alert.message))
                }
            } else {
                showServiceAlert = alert
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                @Composable
                fun BuildDrawerItem(info: DrawerItemInfo) {
                    DrawerItem(
                        info = info,
                        navController = navController,
                        closeDrawer = {
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        currentRoute = currentRoute,
                    )
                }

                val dividerPadding = 4.dp
                val items0 = remember {
                    persistentListOf(
                        DrawerItemInfo(
                            R.string.menu_configuration,
                            R.drawable.description,
                            NavRoutes.CONFIGURATION,
                        ),
                        DrawerItemInfo(R.string.menu_group, R.drawable.view_list, NavRoutes.GROUPS),
                        DrawerItemInfo(R.string.menu_route, R.drawable.directions, NavRoutes.ROUTE),
                        DrawerItemInfo(R.string.settings, R.drawable.settings, NavRoutes.SETTINGS),
                    )
                }
                for (info in items0) BuildDrawerItem(info)
                HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
                val items1 = remember {
                    persistentListOf(
                        DrawerItemInfo(R.string.menu_log, R.drawable.bug_report, NavRoutes.LOG),
                        DrawerItemInfo(
                            R.string.menu_dashboard,
                            R.drawable.transform,
                            NavRoutes.DASHBOARD,
                        ),
                        DrawerItemInfo(
                            R.string.menu_tools,
                            R.drawable.construction,
                            NavRoutes.TOOLS,
                        ),
                    )
                }
                for (info in items1) BuildDrawerItem(info)
                HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.document)) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                        uriHandler.openUri("https://github.com/xchacha20-poly1305/husi/wiki")
                    },
                    modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    icon = {
                        Icon(ImageVector.vectorResource(R.drawable.data_usage), null)
                    },
                )
                BuildDrawerItem(
                    DrawerItemInfo(
                        R.string.menu_about,
                        R.drawable.info,
                        NavRoutes.ABOUT,
                    ),
                )
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
                                Text(stringResource(R.string.close))
                            }
                        },
                        state = tooltipState,
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                }
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.fast_rewind),
                                contentDescription = stringResource(R.string.close),
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
            startDestination = NavRoutes.CONFIGURATION,
        ) {
            fun onDrawerClick() {
                scope.launch {
                    if (drawerState.isOpen) {
                        drawerState.close()
                    } else {
                        drawerState.open()
                    }
                }
            }
            composable(NavRoutes.CONFIGURATION) {
                ConfigurationScreen(
                    mainViewModel = viewModel,
                    onNavigationClick = ::onDrawerClick,
                    selectCallback = null,
                    connection = connection,
                    preSelected = null,
                )
            }
            composable(NavRoutes.GROUPS) {
                GroupScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    connection = connection,
                )
            }
            composable(NavRoutes.ROUTE) {
                RouteScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    connection = connection,
                )
            }
            composable(NavRoutes.SETTINGS) {
                SettingsScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    connection = connection,
                )
            }
            composable(NavRoutes.LOG) {
                LogcatScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    connection = connection,
                )
            }
            composable(NavRoutes.DASHBOARD) {
                DashboardScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    connection = connection,
                    openConnectionDetail = { uuid ->
                        navController.navigate(NavRoutes.connectionsDetail(uuid))
                    },
                )
            }
            composable(
                route = NavRoutes.CONNECTIONS_DETAIL_TEMPLE,
                arguments = listOf(
                    navArgument("uuid") { type = NavType.StringType },
                ),
            ) { entry ->
                val uuid = entry.arguments!!.getString("uuid")!!
                ConnectionDetailScreen(
                    uuid = uuid,
                    popup = { navController.navigateUp() },
                    navigateToRoutes = { navController.navigate(NavRoutes.ROUTE) },
                )
            }
            composable(NavRoutes.TOOLS) {
                ToolsScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    connection = connection,
                )
            }
            composable(NavRoutes.ABOUT) {
                AboutScreen(
                    mainViewModel = viewModel,
                    onDrawerClick = ::onDrawerClick,
                    connection = connection,
                )
            }
        }
    }

    if (showQueryPackageDeniedDialog) AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                context.openPermissionSettings()
                showQueryPackageDeniedDialog = false
            }
        },
        dismissButton = {
            TextButton(stringResource(R.string.no_thanks)) {
                showQueryPackageDeniedDialog = false
                viewModel.showSnackbar(StringOrRes.Res(R.string.have_a_nice_day))
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.warning_amber), null)
        },
        title = { Text(stringResource(R.string.permission_denied)) },
        text = { Text(stringResource(R.string.query_package_denied)) },
    )

    if (showLicenseDialog) AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                DataStore.acceptedLicense = true
                showLicenseDialog = false
            }
        },
        dismissButton = {
            TextButton(stringResource(R.string.no_thanks)) {
                Toast.makeText(context, R.string.have_a_nice_day, Toast.LENGTH_SHORT)
                    .show()
                exit()
                showLicenseDialog = false
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.copyright), null)
        },
        title = { Text(stringResource(R.string.license)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = LICENSE,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
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
                        StringOrRes.ResWithParams(R.string.plugin_unknown, pluginName),
                    )
                } else {
                    AlertDialog(
                        onDismissRequest = { showServiceAlert = null },
                        confirmButton = {
                            TextButton(stringResource(R.string.action_download)) {
                                showServiceAlert = null
                                uriHandler.openUri(plugin.downloadSource.downloadLink)
                            }
                        },
                        dismissButton = {
                            TextButton(stringResource(android.R.string.cancel)) {
                                showServiceAlert = null
                            }
                        },
                        icon = { Icon(ImageVector.vectorResource(R.drawable.error), null) },
                        title = { Text(plugin.displayName) },
                        text = { Text(stringResource(R.string.missing_plugin)) },
                    )
                }
            }

            AlertType.NEED_WIFI_PERMISSION -> {
                AlertDialog(
                    onDismissRequest = { showServiceAlert = null },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            showServiceAlert = null
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            ) {
                                showBackgroundLocationDialog = true
                            } else {
                                finePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(R.string.no_thanks)) {
                            showServiceAlert = null
                        }
                    },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.warning_amber), null) },
                    title = { Text(stringResource(R.string.location_permission_title)) },
                    text = { Text(stringResource(R.string.location_permission_description)) },
                )
            }
        }
    }

    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDialog = false },
            confirmButton = {
                TextButton(stringResource(android.R.string.ok)) {
                    showBackgroundLocationDialog = false
                    @SuppressLint("InlinedApi")
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            },
            dismissButton = {
                TextButton(stringResource(R.string.no_thanks)) {
                    showBackgroundLocationDialog = false
                }
            },
            icon = { Icon(ImageVector.vectorResource(R.drawable.warning_amber), null) },
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_permission_background_description)) },
        )
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
                TextButton(stringResource(dialog.confirmButton.label)) {
                    dialog.confirmButton.onClick()
                    showAlertDialog = null
                }
            },
            dismissButton = dialog.dismissButton?.let { button ->
                {
                    TextButton(stringResource(button.label)) {
                        button.onClick()
                        showAlertDialog = null
                    }
                }
            },
            icon = {
                Icon(
                    ImageVector.vectorResource(
                        if (dialog.dismissButton != null) {
                            R.drawable.question_mark
                        } else {
                            R.drawable.error
                        },
                    ),
                    null,
                )
            },
            title = { Text(stringResource(dialog.title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(dialog.message))
                }
            },
        )
    }
}

@Immutable
private data class DrawerItemInfo(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val route: String,
)

@Composable
private fun DrawerItem(
    modifier: Modifier = Modifier,
    info: DrawerItemInfo,
    navController: NavController,
    closeDrawer: () -> Unit,
    currentRoute: String?,
) {
    NavigationDrawerItem(
        label = { Text(stringResource(info.label)) },
        selected = info.route == currentRoute,
        onClick = {
            closeDrawer()
            if (currentRoute != info.route) {
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
            Icon(ImageVector.vectorResource(info.icon), null)
        },
    )
}
