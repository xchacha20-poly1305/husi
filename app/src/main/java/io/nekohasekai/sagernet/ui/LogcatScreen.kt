@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.SagerFab
import io.nekohasekai.sagernet.compose.SheetActionRow
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.StatsBar
import io.nekohasekai.sagernet.compose.ansiEscape
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.compose.setPlainText
import io.nekohasekai.sagernet.compose.showAndDismissOld
import io.nekohasekai.sagernet.compose.withNavigation
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.utils.SendLog
import kotlinx.coroutines.launch

@Composable
fun LogcatScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    viewModel: LogcatScreenViewModel = viewModel(),
    onDrawerClick: () -> Unit,
    connection: SagerConnection,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)

    var expandMenu by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queryLowerCase = uiState.searchQuery?.lowercase()
    LaunchedEffect(uiState.logs.size) {
        if (!uiState.pause && uiState.logs.isNotEmpty()) {
            listState.scrollToItem(uiState.logs.size - 1)
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarState.showAndDismissOld(
                message = message,
                actionLabel = context.getString(android.R.string.ok),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by connection.status.collectAsStateWithLifecycle()
    val service by connection.service.collectAsStateWithLifecycle()
    LaunchedEffect(service) {
        if (service != null) {
            viewModel.initialize(service!!, connection)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (searchMode) {
                        OutlinedTextField(
                            value = uiState.searchQuery.orEmpty(),
                            onValueChange = { viewModel.setSearchQuery(it.ifEmpty { null }) },
                            placeholder = { Text(stringResource(android.R.string.search_go)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() },
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.5f,
                                ),
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.menu_log))
                    }
                },
                navigationIcon = {
                    if (searchMode) {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.close),
                            contentDescription = stringResource(android.R.string.cancel),
                            onClick = {
                                searchMode = false
                                viewModel.setSearchQuery(null)
                            },
                        )
                    } else {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.menu),
                            contentDescription = stringResource(R.string.menu),
                            onClick = onDrawerClick,
                        )
                    }
                },
                actions = {
                    if (searchMode) return@TopAppBar
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.search),
                        contentDescription = stringResource(android.R.string.search_go),
                        onClick = { searchMode = true },
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(
                            if (uiState.pause) {
                                R.drawable.play_arrow
                            } else {
                                R.drawable.pause
                            },
                        ),
                        contentDescription = stringResource(R.string.pause),
                        onClick = viewModel::togglePause,
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.share),
                        contentDescription = stringResource(R.string.logcat),
                        onClick = { showBottomSheet = true },
                    )
                    Box {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.more_vert),
                            contentDescription = stringResource(R.string.more),
                            onClick = { expandMenu = true },
                        )
                        DropdownMenu(
                            expanded = expandMenu,
                            onDismissRequest = { expandMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_logcat)) },
                                onClick = viewModel::clearLog,
                                leadingIcon = {
                                    Icon(ImageVector.vectorResource(R.drawable.delete_sweep), null)
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
            FloatingActionButtonMenu(
                expanded = scrollHideVisible && uiState.logs.isNotEmpty(),
                button = {
                    SagerFab(
                        visible = scrollHideVisible,
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
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(uiState.logs.lastIndex)
                        }
                    },
                    text = { Text(stringResource(R.string.scroll_to_bottom)) },
                    icon = {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.keyboard_arrow_down),
                            contentDescription = null,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
        },
        bottomBar = {
            if (serviceStatus.state == BaseService.State.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = scrollHideVisible,
                    mainViewModel = mainViewModel,
                    service = service,
                )
            }
        },
    ) { innerPadding ->
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding = innerPadding.withNavigation(),
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
                text = stringResource(R.string.action_copy),
                leadingIcon = {
                    Icon(ImageVector.vectorResource(R.drawable.copy_all), null)
                },
                onClick = {
                    scope.launch {
                        val log = SendLog.buildLog(repo.externalAssetsDir)
                        clipboard.setPlainText(log)
                    }
                },
            )
            SheetActionRow(
                text = stringResource(R.string.share),
                leadingIcon = {
                    Icon(ImageVector.vectorResource(R.drawable.send), null)
                },
                onClick = {
                    scope.launch {
                        try {
                            val logFile = SendLog.buildLog(
                                context.cacheDir,
                                context.getExternalFilesDir(null) ?: context.filesDir,
                                BuildConfig.APPLICATION_ID,
                            )
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("text/x-log")
                                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        .putExtra(
                                            Intent.EXTRA_STREAM,
                                            FileProvider.getUriForFile(
                                                context, BuildConfig.APPLICATION_ID + ".cache",
                                                logFile,
                                            ),
                                        ),
                                    context.getString(androidx.appcompat.R.string.abc_shareactionprovider_share_with),
                                ).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        } catch (e: Exception) {
                            Logs.e(e)
                            snackbarState.showSnackbar(
                                message = e.readableMessage,
                                actionLabel = context.getString(android.R.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
            )
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