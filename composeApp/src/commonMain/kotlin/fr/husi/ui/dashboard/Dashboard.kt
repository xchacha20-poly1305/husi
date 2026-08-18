@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package fr.husi.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.TrafficSortMode
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleSearchInputField
import fr.husi.compose.CapsuleSearchTopBar
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.DropdownMenuSectionHeader
import fr.husi.compose.SagerFabClearance
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Checkbox
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.PrimaryTabRow
import fr.husi.compose.material3.Tab
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.core.remote.RemoteControlManager
import fr.husi.resources.Res
import fr.husi.resources.ascending
import fr.husi.resources.by_destination
import fr.husi.resources.by_download
import fr.husi.resources.by_inbound
import fr.husi.resources.by_matched_rule
import fr.husi.resources.by_source
import fr.husi.resources.by_time
import fr.husi.resources.by_upload
import fr.husi.resources.cancel
import fr.husi.resources.cleaning_services
import fr.husi.resources.close
import fr.husi.resources.connection_status
import fr.husi.resources.connection_status_active
import fr.husi.resources.connection_status_closed
import fr.husi.resources.copy_success
import fr.husi.resources.descending
import fr.husi.resources.ensure_close_all
import fr.husi.resources.group_order_by_delay
import fr.husi.resources.group_order_by_name
import fr.husi.resources.group_order_origin
import fr.husi.resources.have_reset_network
import fr.husi.resources.menu_dashboard
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.no_thanks
import fr.husi.resources.ok
import fr.husi.resources.pause
import fr.husi.resources.play_arrow
import fr.husi.resources.proxy_set
import fr.husi.resources.reset_connections
import fr.husi.resources.search
import fr.husi.resources.search_go
import fr.husi.resources.sort
import fr.husi.resources.sort_mode
import fr.husi.resources.traffic_connections
import fr.husi.resources.traffic_status
import fr.husi.resources.warning_amber
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.RouteSettingsUiState
import fr.husi.ui.StringOrRes
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openvpn.OpenVPNAuthController
import fr.husi.ui.remote.RemoteSessionBanner
import fr.husi.ui.remote.RemoteTargetMenuSection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

private const val PAGE_STATUS = 0
private const val PAGE_CONNECTIONS = 1
private const val PAGE_PROXY_SET = 2

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    openConnectController: OpenConnectAuthController,
    openVPNController: OpenVPNAuthController,
    openRouteSettings: (RouteSettingsUiState) -> Unit,
    onOpenRemoteControl: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current
    val loadPlatformNetworkInfo = rememberLoadPlatformNetworkInfo()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 },
    )

    val remoteControl = koinInject<RemoteControlManager>()
    val dashboardViewModel: DashboardViewModel =
        viewModel {
            DashboardViewModel(
                loadPlatformNetworkInfo = loadPlatformNetworkInfo,
                remoteControl = remoteControl,
            )
        }
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val remoteSession by remoteControl.session.collectAsStateWithLifecycle()
    val targetConnected by remoteControl.targetConnected.collectAsStateWithLifecycle()
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var showResetAlert by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val isConnectionsPage = pagerState.currentPage == PAGE_CONNECTIONS
    val isProxySetPage = pagerState.currentPage == PAGE_PROXY_SET

    val searchBarState = rememberSearchBarState()
    val searchTextFieldState = dashboardViewModel.searchTextFieldState
    val searchInputField: @Composable () -> Unit = {
        CapsuleSearchInputField(
            textFieldState = searchTextFieldState,
            searchBarState = searchBarState,
            onSearch = { focusManager.clearFocus() },
            placeholder = { Text(stringResource(Res.string.search_go)) },
            leadingIcon = {
                Icon(vectorResource(Res.drawable.search), null)
            },
            trailingIcon = if (searchBarState.currentValue == SearchBarValue.Expanded) {
                {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.cancel),
                        onClick = {
                            dashboardViewModel.clearSearchQuery()
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    )
                }
            } else {
                null
            },
        )
    }
    val windowInsets = WindowInsets.safeDrawing

    LaunchedEffect(remoteSession?.server?.id, targetConnected) {
        dashboardViewModel.initialize(targetConnected)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.fastCoerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Surface(color = appBarContainerColor) {
                Column(
                    modifier = Modifier.windowInsetsPadding(
                        windowInsets.only(WindowInsetsSides.Top),
                    ),
                ) {
                    if (isConnectionsPage) {
                        CapsuleSearchTopBar(
                            inputField = searchInputField,
                            navigationIcon = null,
                            actions = {
                                CapsuleActionButton {
                                    SimpleIconButton(
                                        imageVector = if (uiState.isPause) {
                                            vectorResource(Res.drawable.play_arrow)
                                        } else {
                                            vectorResource(Res.drawable.pause)
                                        },
                                        contentDescription = stringResource(Res.string.pause),
                                        onClick = { dashboardViewModel.togglePause() },
                                    )
                                }
                                if (!uiState.isRemote) {
                                    CapsuleActionButton {
                                        SimpleIconButton(
                                            imageVector = vectorResource(Res.drawable.cleaning_services),
                                            contentDescription = stringResource(Res.string.reset_connections),
                                            onClick = { showResetAlert = true },
                                        )
                                    }
                                }
                                CapsuleActionButton {
                                    Box {
                                        SimpleIconButton(
                                            imageVector = vectorResource(Res.drawable.more_vert),
                                            contentDescription = stringResource(Res.string.more),
                                            onClick = { isOverflowMenuExpanded = true },
                                        )

                                        DropdownMenuPopup(
                                            expanded = isOverflowMenuExpanded,
                                            onDismissRequest = { isOverflowMenuExpanded = false },
                                        ) {
                                            RemoteTargetMenuSection(
                                                groupIndex = 0,
                                                groupCount = 4,
                                                onManage = onOpenRemoteControl,
                                                onDismiss = { isOverflowMenuExpanded = false },
                                            )
                                            DropdownMenuGroup(
                                                shapes = MenuDefaults.groupShape(1, 4),
                                            ) {
                                                DropdownMenuSectionHeader(stringResource(Res.string.sort))
                                                DropdownMenuItem(
                                                    selected = !uiState.isDescending,
                                                    onClick = {
                                                        dashboardViewModel.setSortDescending(false)
                                                        isOverflowMenuExpanded = false
                                                    },
                                                    text = { Text(stringResource(Res.string.ascending)) },
                                                    shapes = MenuDefaults.itemShape(0, 2),
                                                )
                                                DropdownMenuItem(
                                                    selected = uiState.isDescending,
                                                    onClick = {
                                                        dashboardViewModel.setSortDescending(true)
                                                        isOverflowMenuExpanded = false
                                                    },
                                                    text = { Text(stringResource(Res.string.descending)) },
                                                    shapes = MenuDefaults.itemShape(1, 2),
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))

                                            DropdownMenuGroup(
                                                shapes = MenuDefaults.groupShape(2, 4),
                                            ) {
                                                DropdownMenuSectionHeader(stringResource(Res.string.sort_mode))
                                                val sortModes = TrafficSortMode.values
                                                for ((i, sortMode) in sortModes.withIndex()) {
                                                    val text = when (sortMode) {
                                                        TrafficSortMode.START -> Res.string.by_time
                                                        TrafficSortMode.INBOUND -> Res.string.by_inbound
                                                        TrafficSortMode.UPLOAD -> Res.string.by_upload
                                                        TrafficSortMode.DOWNLOAD -> Res.string.by_download
                                                        TrafficSortMode.SRC -> Res.string.by_source
                                                        TrafficSortMode.DST -> Res.string.by_destination
                                                        TrafficSortMode.MATCHED_RULE -> Res.string.by_matched_rule
                                                        else -> throw IllegalArgumentException("$sortMode impossible")
                                                    }
                                                    DropdownMenuItem(
                                                        checked = sortMode == uiState.sortMode,
                                                        onCheckedChange = {
                                                            if (!it) return@DropdownMenuItem
                                                            isOverflowMenuExpanded = false
                                                            dashboardViewModel.setSortMode(sortMode)
                                                        },
                                                        text = { Text(stringResource(text)) },
                                                        shapes = MenuDefaults.itemShape(
                                                            i,
                                                            sortModes.size,
                                                        ),
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))

                                            DropdownMenuGroup(
                                                shapes = MenuDefaults.groupShape(3, 4),
                                            ) {
                                                DropdownMenuSectionHeader(stringResource(Res.string.connection_status))
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(Res.string.connection_status_active)) },
                                                    onClick = {
                                                        dashboardViewModel.setQueryActivate(!uiState.showActivate)
                                                    },
                                                    leadingIcon = {
                                                        Checkbox(
                                                            checked = uiState.showActivate,
                                                            onCheckedChange = null,
                                                        )
                                                    },
                                                    shape = MenuDefaults.itemShape(0, 2).shape,
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(Res.string.connection_status_closed)) },
                                                    onClick = {
                                                        dashboardViewModel.setQueryClosed(!uiState.showClosed)
                                                    },
                                                    leadingIcon = {
                                                        Checkbox(
                                                            checked = uiState.showClosed,
                                                            onCheckedChange = null,
                                                        )
                                                    },
                                                    shape = MenuDefaults.itemShape(1, 2).shape,
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
                            scrollBehavior = scrollBehavior,
                        )
                    } else {
                        val groupCount = if (isProxySetPage) 2 else 1
                        CapsuleTopBar(
                            navigationIcon = null,
                            title = { Text(stringResource(Res.string.menu_dashboard)) },
                            actions = {
                                CapsuleActionButton {
                                    Box {
                                        SimpleIconButton(
                                            imageVector = vectorResource(Res.drawable.more_vert),
                                            contentDescription = stringResource(Res.string.more),
                                            onClick = { isOverflowMenuExpanded = true },
                                        )
                                        DropdownMenuPopup(
                                            expanded = isOverflowMenuExpanded,
                                            onDismissRequest = { isOverflowMenuExpanded = false },
                                        ) {
                                            RemoteTargetMenuSection(
                                                groupIndex = 0,
                                                groupCount = groupCount,
                                                onManage = onOpenRemoteControl,
                                                onDismiss = { isOverflowMenuExpanded = false },
                                            )
                                            if (isProxySetPage) {
                                                Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
                                                DropdownMenuGroup(
                                                    shapes = MenuDefaults.groupShape(1, groupCount),
                                                ) {
                                                    DropdownMenuSectionHeader(stringResource(Res.string.sort_mode))
                                                    val orders = ProxySetOrder.values
                                                    for ((i, order) in orders.withIndex()) {
                                                        val text = when (order) {
                                                            ProxySetOrder.ORIGIN -> Res.string.group_order_origin
                                                            ProxySetOrder.BY_NAME -> Res.string.group_order_by_name
                                                            ProxySetOrder.BY_DELAY -> Res.string.group_order_by_delay
                                                            else -> continue
                                                        }
                                                        DropdownMenuItem(
                                                            selected = uiState.proxySetOrder == order,
                                                            onClick = {
                                                                isOverflowMenuExpanded = false
                                                                dashboardViewModel.setProxySetOrder(order)
                                                            },
                                                            text = { Text(stringResource(text)) },
                                                            shapes = MenuDefaults.itemShape(i, orders.size),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
                            scrollBehavior = scrollBehavior,
                        )
                    }
                    RemoteSessionBanner()

                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = appBarContainerColor,
                    ) {
                        Tab(
                            text = { Text(stringResource(Res.string.traffic_status)) },
                            selected = pagerState.currentPage == PAGE_STATUS,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(PAGE_STATUS)
                                }
                            },
                        )
                        Tab(
                            text = { Text(stringResource(Res.string.traffic_connections)) },
                            selected = pagerState.currentPage == PAGE_CONNECTIONS,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(PAGE_CONNECTIONS)
                                }
                            },
                        )
                        Tab(
                            text = { Text(stringResource(Res.string.proxy_set)) },
                            selected = pagerState.currentPage == PAGE_PROXY_SET,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(PAGE_PROXY_SET)
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val bottomPadding = max(innerPadding.calculateBottomPadding(), SagerFabClearance)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_STATUS -> DashboardStatusScreen(
                        uiState = uiState,
                        openConnectController = openConnectController,
                        openVPNController = openVPNController,
                        bottomPadding = bottomPadding,
                        selectClashMode = { dashboardViewModel.setClashMode(it) },
                        showError = { message ->
                            snackbar.show(StringOrRes.Direct(message))
                        },
                        onCopySuccess = {
                            snackbar.show(StringOrRes.Res(Res.string.copy_success))
                        },
                    )

                    PAGE_CONNECTIONS -> DashboardConnectionsScreen(
                        uiState = uiState,
                        bottomPadding = bottomPadding,
                        resolveProcessInfo = dashboardViewModel::resolveProcessInfo,
                        closeConnection = { uuid ->
                            dashboardViewModel.closeConnection(uuid)
                        },
                        onConnectionClick = dashboardViewModel::selectConnection,
                    )

                    PAGE_PROXY_SET -> DashboardProxySetScreen(
                        uiState = uiState,
                        bottomPadding = bottomPadding,
                        selectProxy = { group, proxy ->
                            dashboardViewModel.selectOutbound(group, proxy)
                        },
                        urlTestForSingle = dashboardViewModel::urlTestForSingle,
                        urlTestForGroup = dashboardViewModel::urlTestForGroup,
                    )

                    else -> error("impossible")
                }
            }
        }
    }

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = searchInputField,
    ) {
        DashboardConnectionsScreen(
            uiState = uiState.copy(connections = uiState.filteredConnections),
            bottomPadding = 0.dp,
            resolveProcessInfo = dashboardViewModel::resolveProcessInfo,
            closeConnection = { uuid ->
                dashboardViewModel.closeConnection(uuid)
            },
            onConnectionClick = dashboardViewModel::selectConnection,
        )
    }

    uiState.selectedConnection?.let { connection ->
        ModalBottomSheet(
            onDismissRequest = { dashboardViewModel.selectConnection(null) },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            ConnectionDetailSheet(
                connection = connection,
                resolveProcessInfo = dashboardViewModel::resolveProcessInfo,
                closeConnection = dashboardViewModel::closeConnection,
                onDismiss = { dashboardViewModel.selectConnection(null) },
                openRouteSettings = { draft ->
                    dashboardViewModel.selectConnection(null)
                    openRouteSettings(draft)
                },
            )
        }
    }

    if (showResetAlert) AlertDialog(
        onDismissRequest = { showResetAlert = false },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                dashboardViewModel.resetNetwork()
                snackbar.show(StringOrRes.Res(Res.string.have_reset_network))
                showResetAlert = false
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.no_thanks)) {
                showResetAlert = false
            }
        },
        icon = {
            Icon(vectorResource(Res.drawable.warning_amber), null)
        },
        title = { Text(stringResource(Res.string.reset_connections)) },
        text = { Text(stringResource(Res.string.ensure_close_all, uiState.connections.size)) },
    )

}
