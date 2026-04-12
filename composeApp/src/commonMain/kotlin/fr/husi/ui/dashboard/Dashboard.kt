@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package fr.husi.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import fr.husi.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import fr.husi.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import fr.husi.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import fr.husi.compose.material3.Tab
import fr.husi.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceIn
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.TrafficSortMode
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.ui.MainViewModelAlertDialog
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.SagerFab
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.StatsBar
import fr.husi.compose.TextButton
import fr.husi.compose.paddingExceptBottom
import fr.husi.ui.MainViewModel
import fr.husi.ui.MainViewModelUiEvent
import fr.husi.ui.getStringOrRes
import kotlinx.coroutines.launch
import kotlin.math.max
import fr.husi.resources.*
import fr.husi.repository.resolveRepository

private const val PAGE_STATUS = 0
private const val PAGE_CONNECTIONS = 1
private const val PAGE_PROXY_SET = 2

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onDrawerClick: () -> Unit,
    openConnectionDetail: (uuid: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    var showAlertDialog by remember { mutableStateOf<MainViewModelUiEvent.AlertDialog?>(null) }
    val loadPlatformNetworkInfo = rememberLoadPlatformNetworkInfo()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 },
    )

    val dashboardViewModel: DashboardViewModel = viewModel { DashboardViewModel(loadPlatformNetworkInfo) }
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var showResetAlert by remember { mutableStateOf(false) }
    var bottomVisible by remember { mutableStateOf(true) }
    var scaffoldHeightPx by remember { mutableIntStateOf(0) }
    var fabTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var fabHeightPx by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val isConnectionsPage = pagerState.currentPage == PAGE_CONNECTIONS

    val searchBarState = rememberSearchBarState()
    val searchTextFieldState = dashboardViewModel.searchTextFieldState
    val searchInputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
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
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors()
    val overlappedFraction by remember(scrollBehavior) {
        derivedStateOf {
            if (scrollBehavior.scrollOffsetLimit != 0f) {
                1 -
                        ((scrollBehavior.scrollOffsetLimit - scrollBehavior.contentOffset)
                            .fastCoerceIn(
                                scrollBehavior.scrollOffsetLimit,
                                0f,
                            ) / scrollBehavior.scrollOffsetLimit)
            } else {
                0f
            }
        }
    }
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            appBarWithSearchColors.appBarContainerColor,
            appBarWithSearchColors.scrolledAppBarContainerColor,
            overlappedFraction.fastCoerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )
    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    LaunchedEffect(serviceStatus.state.connected) {
        dashboardViewModel.initialize(serviceStatus.state.connected)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(
                modifier = Modifier
                    .background(appBarContainerColor)
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top)),
            ) {
                if (isConnectionsPage) {
                    AppBarWithSearch(
                        state = searchBarState,
                        inputField = searchInputField,
                        navigationIcon = {
                            PlatformMenuIcon(
                                imageVector = vectorResource(Res.drawable.menu),
                                contentDescription = stringResource(Res.string.menu),
                                onClick = onDrawerClick,
                            )
                        },
                        actions = {
                            SimpleIconButton(
                                imageVector = if (uiState.isPause) {
                                    vectorResource(Res.drawable.play_arrow)
                                } else {
                                    vectorResource(Res.drawable.pause)
                                },
                                contentDescription = stringResource(Res.string.pause),
                                onClick = { dashboardViewModel.togglePause() },
                            )
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.cleaning_services),
                                contentDescription = stringResource(Res.string.reset_connections),
                                onClick = { showResetAlert = true },
                            )

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
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShape(0, 3),
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                MenuDefaults.Label {
                                                    Text(
                                                        text = stringResource(Res.string.sort),
                                                        style = MaterialTheme.typography.titleSmall,
                                                    )
                                                }
                                            },
                                            onClick = {},
                                        )
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
                                        shapes = MenuDefaults.groupShape(1, 3),
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                MenuDefaults.Label {
                                                    Text(
                                                        text = stringResource(Res.string.sort_mode),
                                                        style = MaterialTheme.typography.titleSmall,
                                                    )
                                                }
                                            },
                                            onClick = {},
                                        )
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
                                                shapes = MenuDefaults.itemShape(i, sortModes.size),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))

                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShape(2, 3),
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                MenuDefaults.Label {
                                                    Text(
                                                        text = stringResource(Res.string.connection_status),
                                                        style = MaterialTheme.typography.titleSmall,
                                                    )
                                                }
                                            },
                                            onClick = {},
                                        )
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
                                        )
                                    }
                                }
                            }
                        },
                        colors = appBarWithSearchColors,
                        scrollBehavior = scrollBehavior,
                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
                    )
                } else {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            PlatformMenuIcon(
                                imageVector = vectorResource(Res.drawable.menu),
                                contentDescription = stringResource(Res.string.menu),
                                onClick = onDrawerClick,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
                    )
                }

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
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        fabTopPx = coordinates.positionInRoot().y
                    }
                    .onSizeChanged { fabHeightPx = it.height },
            ) {
                SagerFab(
                    visible = bottomVisible,
                    state = serviceStatus.state,
                    showSnackbar = { message ->
                        scope.launch {
                            snackbarState.showSnackbar(
                                message = getStringOrRes(message),
                                actionLabel = resolveRepository().getString(Res.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (serviceStatus.state == ServiceState.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = bottomVisible,
                    mainViewModel = mainViewModel,
                )
            }
        },
    ) { innerPadding ->
        val density = LocalDensity.current
        val innerBottomPx = with(density) { innerPadding.calculateBottomPadding().roundToPx() }
        val fabReservedBottomPx by remember(scaffoldHeightPx, fabTopPx) {
            derivedStateOf {
                if (scaffoldHeightPx <= 0 || fabTopPx.isNaN()) {
                    0
                } else {
                    (scaffoldHeightPx - fabTopPx.toInt()).fastCoerceAtLeast(0)
                }
            }
        }
        val effectiveFabReservedBottomPx by remember(
            bottomVisible,
            fabReservedBottomPx,
            fabHeightPx,
        ) {
            derivedStateOf {
                if (bottomVisible && fabHeightPx > 0) fabReservedBottomPx else 0
            }
        }
        val bottomPaddingPx = max(innerBottomPx, effectiveFabReservedBottomPx)
        val bottomPadding = with(density) { bottomPaddingPx.toDp() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { scaffoldHeightPx = it.height }
                .paddingExceptBottom(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_STATUS -> DashboardStatusScreen(
                        uiState = uiState,
                        bottomPadding = bottomPadding,
                        selectClashMode = { dashboardViewModel.setClashMode(it) },
                        onCopySuccess = {
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = resolveRepository().getString(Res.string.copy_success),
                                    actionLabel = resolveRepository().getString(Res.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        onVisibleChange = { bottomVisible = it },
                    )

                    PAGE_CONNECTIONS -> DashboardConnectionsScreen(
                        uiState = uiState,
                        bottomPadding = bottomPadding,
                        resolveProcessInfo = dashboardViewModel::resolveProcessInfo,
                        closeConnection = { uuid ->
                            dashboardViewModel.closeConnection(uuid)
                        },
                        openDetail = openConnectionDetail,
                        onVisibleChange = { bottomVisible = it },
                    )

                    PAGE_PROXY_SET -> DashboardProxySetScreen(
                        uiState = uiState,
                        bottomPadding = bottomPadding,
                        selectProxy = { group, proxy ->
                            dashboardViewModel.selectOutbound(group, proxy)
                        },
                        urlTestForSingle = dashboardViewModel::urlTestForSingle,
                        urlTestForGroup = dashboardViewModel::urlTestForGroup,
                        onVisibleChange = { bottomVisible = it },
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
            openDetail = openConnectionDetail,
            onVisibleChange = {},
        )
    }

    if (showResetAlert) AlertDialog(
        onDismissRequest = { showResetAlert = false },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                dashboardViewModel.resetNetwork()
                scope.launch {
                    snackbarState.showSnackbar(
                        message = resolveRepository().getString(Res.string.have_reset_network),
                        actionLabel = resolveRepository().getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }
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

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = resolveRepository().getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                is MainViewModelUiEvent.AlertDialog -> showAlertDialog = event

            }
        }
    }

    showAlertDialog?.let { dialog ->
        MainViewModelAlertDialog(dialog) {
            showAlertDialog = null
        }
    }
}
