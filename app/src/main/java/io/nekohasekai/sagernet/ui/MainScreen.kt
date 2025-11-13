package io.nekohasekai.sagernet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.NavRoutes
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
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
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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

    DismissibleNavigationDrawer(
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
                HorizontalDivider()
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
                HorizontalDivider()
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
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = NavRoutes.CONFIGURATION,
        ) {
            fun openDrawer() {
                scope.launch {
                    drawerState.open()
                }
            }
            composable(NavRoutes.CONFIGURATION) {
                ConfigurationScreen(
                    mainViewModel = viewModel,
                    onNavigationClick = ::openDrawer,
                    selectCallback = null,
                    preSelected = null,
                )
            }
            composable(NavRoutes.GROUPS) {
                GroupScreen(
                    mainViewModel = viewModel,
                    openDrawer = ::openDrawer,
                )
            }
            composable(NavRoutes.ROUTE) {
                RouteScreen(
                    mainViewModel = viewModel,
                    openDrawer = ::openDrawer,
                )
            }
            composable(NavRoutes.SETTINGS) {
                SettingsScreen(
                    mainViewModel = viewModel,
                    openDrawer = ::openDrawer,
                )
            }
            composable(NavRoutes.LOG) {
                LogcatScreen(
                    mainViewModel = viewModel,
                    openDrawer = ::openDrawer,
                )
            }
            composable(NavRoutes.DASHBOARD) {
                DashboardScreen(
                    mainViewModel = viewModel,
                    openDrawer = ::openDrawer,
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
                    connection = connection,
                    popup = { navController.navigateUp() },
                )
            }
            composable(NavRoutes.TOOLS) {
                ToolsScreen(
                    mainViewModel = viewModel,
                    openDrawer = ::openDrawer,
                )
            }
            composable(NavRoutes.ABOUT) {
                AboutScreen(
                    mainViewModel = viewModel,
                    openDrawer = ::openDrawer,
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
            SelectionContainer {
                Text(
                    text = LICENSE,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
    )

    var showNormalAlert by remember { mutableStateOf<StringOrRes?>(null) }
    var showConfirmImportProfile by remember {
        mutableStateOf<MainViewModelUiEvent.ConfirmImportProfile?>(null)
    }
    var showConfirmImportSubscription by remember {
        mutableStateOf<MainViewModelUiEvent.ConfirmImportSubscription?>(null)
    }
    var showImportSubscriptionOrHttp by remember {
        mutableStateOf<MainViewModelUiEvent.ImportSubscriptionOrHttp?>(null)
    }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Alert -> showNormalAlert = event.message
                is MainViewModelUiEvent.ConfirmImportProfile -> showConfirmImportProfile = event
                is MainViewModelUiEvent.ConfirmImportSubscription -> {
                    showConfirmImportSubscription = null
                }

                is MainViewModelUiEvent.ImportSubscriptionOrHttp -> {
                    showImportSubscriptionOrHttp = null
                }

                else -> {}
            }
        }
    }
    if (showNormalAlert != null) AlertDialog(
        onDismissRequest = { showNormalAlert = null },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                showNormalAlert = null
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.error), null)
        },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(stringResource(showNormalAlert!!)) },
    )
    if (showConfirmImportProfile != null) AlertDialog(
        onDismissRequest = { showConfirmImportProfile = null },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                showConfirmImportProfile!!.confirm()
                showConfirmImportProfile = null
            }
        },
        dismissButton = {
            TextButton(stringResource(android.R.string.cancel)) {
                showConfirmImportProfile = null
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.question_mark), null)
        },
        title = { Text(stringResource(R.string.profile_import)) },
        text = {
            Text(
                stringResource(
                    R.string.profile_import_message,
                    showConfirmImportProfile!!.name,
                ),
            )
        },
    )
    if (showConfirmImportSubscription != null) AlertDialog(
        onDismissRequest = { showConfirmImportSubscription = null },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                showConfirmImportSubscription!!.confirm()
                showConfirmImportSubscription = null
            }
        },
        dismissButton = {
            TextButton(stringResource(android.R.string.ok)) {
                showConfirmImportSubscription = null
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.question_mark), null)
        },
        title = { Text(stringResource(R.string.subscription_import)) },
        text = {
            Text(
                stringResource(
                    R.string.subscription_import_message,
                    showConfirmImportSubscription!!.detail,
                ),
            )
        },
    )
    if (showImportSubscriptionOrHttp != null) AlertDialog(
        onDismissRequest = { showImportSubscriptionOrHttp = null },
        confirmButton = {
            TextButton(stringResource(R.string.subscription_import)) {
                showImportSubscriptionOrHttp!!.asSubscription()
                showImportSubscriptionOrHttp = null
            }
        },
        dismissButton = {
            TextButton(stringResource(R.string.import_http_url)) {
                showImportSubscriptionOrHttp!!.asProxy()
                showImportSubscriptionOrHttp = null
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.question_mark), null)
        },
        title = { Text(stringResource(R.string.subscription_import)) },
        text = { Text(stringResource(R.string.import_http_url)) },
    )
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