@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.SagerFab
import fr.husi.compose.SheetActionRow
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.StatsBar
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.ansiEscape
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.setPlainText
import fr.husi.ktx.readableMessage
import fr.husi.ktx.showAndDismissOld
import fr.husi.repository.repo
import fr.husi.utils.SendLog
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import kotlin.math.max
import fr.husi.resources.*

@Composable
fun LogcatScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    viewModel: LogcatScreenViewModel = viewModel { LogcatScreenViewModel() },
    onDrawerClick: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)
    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    val canScroll by remember {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }
    var autoScroll by remember { mutableStateOf(true) }
    var scaffoldHeightPx by remember { mutableIntStateOf(0) }
    var fabTopPx by remember { mutableFloatStateOf(Float.NaN) }
    val isAtBottom by remember {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    val searchBarVisible =
        scrollHideVisible && (canScroll || viewModel.searchTextFieldState.text.isNotEmpty())

    var expandMenu by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queryLowerCase = uiState.searchQuery?.lowercase()
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling) {
                    wasScrolling = true
                } else if (wasScrolling) {
                    autoScroll = isAtBottom
                    wasScrolling = false
                }
            }
    }
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isEmpty()) {
            autoScroll = true
            return@LaunchedEffect
        }
        if (!uiState.pause && autoScroll) {
            listState.scrollToItem(uiState.logs.lastIndex)
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarState.showAndDismissOld(
                message = message,
                actionLabel = repo.getString(Res.string.ok),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(Res.string.menu_log))
                },
                navigationIcon = {
                    PlatformMenuIcon(
                        imageVector = vectorResource(Res.drawable.menu),
                        contentDescription = stringResource(Res.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                actions = {
                    SimpleIconButton(
                        imageVector = vectorResource(
                            if (uiState.pause) {
                                Res.drawable.play_arrow
                            } else {
                                Res.drawable.pause
                            },
                        ),
                        contentDescription = stringResource(Res.string.pause),
                        onClick = viewModel::togglePause,
                    )
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.share),
                        contentDescription = stringResource(Res.string.logcat),
                        onClick = { showBottomSheet = true },
                    )
                    Box {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.more_vert),
                            contentDescription = stringResource(Res.string.more),
                            onClick = { expandMenu = true },
                        )
                        DropdownMenu(
                            expanded = expandMenu,
                            onDismissRequest = { expandMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.clear_logcat)) },
                                onClick = viewModel::clearLog,
                                leadingIcon = {
                                    Icon(vectorResource(Res.drawable.delete_sweep), null)
                                },
                                colors = MenuDefaults.itemColors().copy(
                                    leadingIconColor = MaterialTheme.colorScheme.error,
                                ),
                            )
                            HorizontalDivider()
                            LogLevel.entries.forEach { level ->
                                DropdownMenuItem(
                                    text = { Text(level.name) },
                                    onClick = {
                                        viewModel.setLogLevel(level)
                                        expandMenu = false
                                    },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = uiState.logLevel == level,
                                            onClick = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            Box(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    fabTopPx = coordinates.positionInRoot().y
                },
            ) {
                FloatingActionButtonMenu(
                    expanded = uiState.logs.isNotEmpty(),
                    button = {
                        SagerFab(
                            visible = true,
                            state = serviceStatus.state,
                            showSnackbar = { message ->
                                scope.launch {
                                    snackbarState.showSnackbar(
                                        message = getStringOrRes(message),
                                        actionLabel = repo.getString(Res.string.ok),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                        )
                    },
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(uiState.logs.lastIndex)
                            }
                        },
                        text = { Text(stringResource(Res.string.scroll_to_bottom)) },
                        icon = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.keyboard_arrow_down),
                                contentDescription = null,
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
            }
        },
        bottomBar = {
            if (serviceStatus.state == ServiceState.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = true,
                    mainViewModel = mainViewModel,
                )
            }
        },
    ) { innerPadding ->
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val innerBottomPx = with(density) { innerPadding.calculateBottomPadding().roundToPx() }
        val fabReservedBottomPx by remember(scaffoldHeightPx, fabTopPx) {
            derivedStateOf {
                if (scaffoldHeightPx <= 0 || fabTopPx.isNaN()) {
                    0
                } else {
                    (scaffoldHeightPx - fabTopPx.toInt()).coerceAtLeast(0)
                }
            }
        }
        val bottomPaddingPx = max(innerBottomPx, fabReservedBottomPx)
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = with(density) { bottomPaddingPx.toDp() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { scaffoldHeightPx = it.height },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            state = listState,
                            contentPadding = contentPadding,
                        ) {
                            itemsIndexed(
                                items = uiState.logs,
                                key = { index, _ -> index },
                                contentType = { _, _ -> 0 },
                            ) { _, logLine ->
                                LogCard(
                                    logLine = logLine.message,
                                    highlightQuery = queryLowerCase,
                                )
                            }
                        }
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }

            AnimatedVisibility(
                visible = searchBarVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                DockedSearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            state = viewModel.searchTextFieldState,
                            onSearch = { focusManager.clearFocus() },
                            expanded = false,
                            onExpandedChange = {},
                            leadingIcon = {
                                Icon(vectorResource(Res.drawable.search), null)
                            },
                            trailingIcon = if (viewModel.searchTextFieldState.text.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = viewModel::clearSearchQuery,
                                    ) {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.close),
                                            contentDescription = stringResource(Res.string.cancel),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.padding(
                        top = innerPadding.calculateTopPadding() + 24.dp,
                    ),
                    colors = SearchBarDefaults.colors().run {
                        copy(
                            containerColor = containerColor.copy(alpha = 0.8f),
                        )
                    },
                    content = {},
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = repo.getString(Res.string.ok),
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

                else -> {}
            }
        }
    }

    if (showBottomSheet) ModalBottomSheet(
        onDismissRequest = { showBottomSheet = false },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            SheetActionRow(
                text = stringResource(Res.string.action_copy),
                leadingIcon = {
                    Icon(vectorResource(Res.drawable.copy_all), null)
                },
                onClick = {
                    scope.launch {
                        val log = SendLog.buildLog(repo.externalAssetsDir)
                        clipboard.setPlainText(log)
                    }
                },
            )
            ShareActionRow(scope) { e->
                snackbarState.showSnackbar(
                    message = e.readableMessage,
                    actionLabel = repo.getString(Res.string.ok),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
}

@Composable
private fun LogCard(
    modifier: Modifier = Modifier,
    logLine: String,
    highlightQuery: String? = null,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = logLine.ansiEscape(highlightQuery),
            modifier = Modifier.padding(12.dp),
        )
    }
}
