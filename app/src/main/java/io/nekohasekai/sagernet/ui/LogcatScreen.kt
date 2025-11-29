package io.nekohasekai.sagernet.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.SagerFab
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.StatsBar
import io.nekohasekai.sagernet.compose.ansiEscape
import io.nekohasekai.sagernet.compose.paddingWithNavigation
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.compose.showAndDismissOld
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
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
    val scope = rememberCoroutineScope()

    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.logs.size) {
        if (!uiState.pinScroll && uiState.logs.isNotEmpty()) {
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

    val serviceStatus by connection.status.collectAsStateWithLifecycle()
    val service by connection.service.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_log)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.menu),
                        contentDescription = stringResource(R.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                actions = {
                    SimpleIconButton(
                        imageVector = if (uiState.pinScroll) {
                            ImageVector.vectorResource(R.drawable.sailing)
                        } else {
                            ImageVector.vectorResource(R.drawable.push_pin)
                        },
                        contentDescription = stringResource(R.string.pin_log),
                        onClick = { viewModel.togglePinScroll() },
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.send),
                        contentDescription = stringResource(R.string.logcat),
                    ) {
                        scope.launch {
                            try {
                                SendLog.sendLog(context, "husi")
                            } catch (e: Exception) {
                                Logs.e(e)
                                snackbarState.showAndDismissOld(
                                    message = e.readableMessage,
                                    actionLabel = context.getString(android.R.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    }
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.delete_sweep),
                        contentDescription = stringResource(R.string.clear_logcat),
                        onClick = { viewModel.clearLog() },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
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
                contentPadding = innerPadding.paddingWithNavigation(),
            ) {
                itemsIndexed(
                    items = uiState.logs,
                    key = { index, _ -> index },
                    contentType = { _, _ -> 0 },
                ) { _, logLine ->
                    LogCard(logLine = logLine)
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
}

@Composable
private fun LogCard(
    modifier: Modifier = Modifier,
    logLine: String,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = logLine.ansiEscape(),
            modifier = Modifier.padding(12.dp),
        )
    }
}