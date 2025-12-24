@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.SagerFab
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.StatsBar
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainViewModel
import io.nekohasekai.sagernet.ui.MainViewModelUiEvent
import io.nekohasekai.sagernet.ui.getStringOrRes
import kotlinx.coroutines.launch

private const val PAGE_STATUS = 0
private const val PAGE_CONNECTIONS = 1
private const val PAGE_PROXY_SET = 2

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    viewModel: DashboardViewModel = viewModel(),
    onDrawerClick: () -> Unit,
    connection: SagerConnection,
    openConnectionDetail: (uuid: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 },
    )

    var isSearchActive by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var showResetAlert by remember { mutableStateOf(false) }
    var bottomVisible by remember { mutableStateOf(true) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.coerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )
    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by connection.status.collectAsStateWithLifecycle()
    val service by connection.service.collectAsStateWithLifecycle()
    LaunchedEffect(System.identityHashCode(service)) {
        viewModel.initialize(service)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(stringResource(android.R.string.search_go)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                },
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                unfocusedIndicatorColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                unfocusedContainerColor =
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                focusedContainerColor =
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.menu_dashboard))
                    }
                },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.menu),
                        contentDescription = stringResource(R.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                actions = {
                    if (isSearchActive) SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                    ) {
                        isSearchActive = false
                        viewModel.setSearchQuery("")
                    } else {
                        if (pagerState.currentPage != PAGE_CONNECTIONS) return@TopAppBar
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.search),
                            contentDescription = stringResource(android.R.string.search_go),
                        ) {
                            isSearchActive = true
                        }
                        SimpleIconButton(
                            imageVector = if (uiState.isPause) {
                                ImageVector.vectorResource(R.drawable.play_arrow)
                            } else {
                                ImageVector.vectorResource(R.drawable.pause)
                            },
                            contentDescription = stringResource(R.string.pause),
                            onClick = { viewModel.togglePause() },
                        )
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.cleaning_services),
                            contentDescription = stringResource(R.string.reset_connections),
                            onClick = { showResetAlert = true },
                        )

                        Box {
                            SimpleIconButton(
                                imageVector = ImageVector.vectorResource(R.drawable.more_vert),
                                contentDescription = stringResource(R.string.more),
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
                                                    text = stringResource(R.string.sort),
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                            }
                                        },
                                        onClick = {},
                                    )
                                    DropdownMenuItem(
                                        selected = !uiState.isDescending,
                                        onClick = {
                                            viewModel.setSortDescending(false)
                                            isOverflowMenuExpanded = false
                                        },
                                        text = { Text(stringResource(R.string.ascending)) },
                                        shapes = MenuDefaults.itemShape(0, 2),
                                    )
                                    DropdownMenuItem(
                                        selected = uiState.isDescending,
                                        onClick = {
                                            viewModel.setSortDescending(true)
                                            isOverflowMenuExpanded = false
                                        },
                                        text = { Text(stringResource(R.string.descending)) },
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
                                                    text = stringResource(R.string.sort_mode),
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                            }
                                        },
                                        onClick = {},
                                    )
                                    val sortModes = TrafficSortMode.values
                                    for ((i, sortMode) in sortModes.withIndex()) {
                                        val text = when (sortMode) {
                                            TrafficSortMode.START -> R.string.by_time
                                            TrafficSortMode.INBOUND -> R.string.by_inbound
                                            TrafficSortMode.UPLOAD -> R.string.by_upload
                                            TrafficSortMode.DOWNLOAD -> R.string.by_download
                                            TrafficSortMode.SRC -> R.string.by_source
                                            TrafficSortMode.DST -> R.string.by_destination
                                            TrafficSortMode.MATCHED_RULE -> R.string.by_matched_rule
                                            else -> throw IllegalArgumentException("$sortMode impossible")
                                        }
                                        DropdownMenuItem(
                                            checked = sortMode == uiState.sortMode,
                                            onCheckedChange = {
                                                if (!it) return@DropdownMenuItem
                                                isOverflowMenuExpanded = false
                                                viewModel.setSortMode(sortMode)
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
                                                    text = stringResource(R.string.connection_status),
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                            }
                                        },
                                        onClick = {},
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.connection_status_active)) },
                                        onClick = {
                                            viewModel.setQueryActivate(!uiState.showActivate)
                                        },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = uiState.showActivate,
                                                onCheckedChange = null,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.connection_status_closed)) },
                                        onClick = {
                                            viewModel.setQueryClosed(!uiState.showClosed)
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
                    }
                },
                colors = topAppBarColors.copy(
                    containerColor = appBarContainerColor,
                    scrolledContainerColor = appBarContainerColor,
                ),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            SagerFab(
                visible = bottomVisible,
                state = serviceStatus.state,
                showSnackbar = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = context.getStringOrRes(message),
                            actionLabel = context.getString(android.R.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (serviceStatus.state == BaseService.State.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = bottomVisible,
                    mainViewModel = mainViewModel,
                    service = service,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = appBarContainerColor,
            ) {
                Tab(
                    text = { Text(stringResource(R.string.traffic_status)) },
                    selected = pagerState.currentPage == PAGE_STATUS,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_STATUS)
                        }
                    },
                )
                Tab(
                    text = { Text(stringResource(R.string.traffic_connections)) },
                    selected = pagerState.currentPage == PAGE_CONNECTIONS,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_CONNECTIONS)
                        }
                    },
                )
                Tab(
                    text = { Text(stringResource(R.string.proxy_set)) },
                    selected = pagerState.currentPage == PAGE_PROXY_SET,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_PROXY_SET)
                        }
                    },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_STATUS -> DashboardStatusScreen(
                        uiState = uiState,
                        selectClashMode = { service?.clashMode = it },
                        onCopySuccess = {
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = context.getString(R.string.copy_success),
                                    actionLabel = context.getString(android.R.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        onVisibleChange = { bottomVisible = it },
                    )

                    PAGE_CONNECTIONS -> DashboardConnectionsScreen(
                        uiState = uiState,
                        closeConnection = { uuid ->
                            connection.service.value?.closeConnection(uuid)
                        },
                        openDetail = openConnectionDetail,
                        onVisibleChange = { bottomVisible = it },
                    )

                    PAGE_PROXY_SET -> DashboardProxySetScreen(
                        uiState = uiState,
                        selectProxy = { group, proxy ->
                            connection.service.value?.groupSelect(group, proxy)
                        },
                        urlTestForGroup = { group ->
                            scope.launch {
                                viewModel.setTesting(group, true)
                                connection.service.value?.groupURLTest(
                                    group,
                                    DataStore.connectionTestTimeout,
                                )
                                viewModel.setTesting(group, false)
                            }
                        },
                        onVisibleChange = { bottomVisible = it },
                    )

                    else -> error("impossible")
                }
            }
        }
    }

    if (showResetAlert) AlertDialog(
        onDismissRequest = { showResetAlert = false },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                connection.service.value?.resetNetwork()
                scope.launch {
                    snackbarState.showSnackbar(
                        message = context.getString(R.string.have_reset_network),
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }
                showResetAlert = false
            }
        },
        dismissButton = {
            TextButton(stringResource(R.string.no_thanks)) {
                showResetAlert = false
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.warning_amber), null)
        },
        title = { Text(stringResource(R.string.reset_connections)) },
        text = { Text(stringResource(R.string.ensure_close_all, uiState.connections.size)) },
    )

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                else -> {}

            }
        }
    }
}