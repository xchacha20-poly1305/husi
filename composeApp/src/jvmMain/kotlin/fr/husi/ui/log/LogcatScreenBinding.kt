package fr.husi.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.DataStore
import fr.husi.ktx.readableMessage
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import fr.husi.ui.remote.RemoteSessionBanner
import fr.husi.ui.remote.RemoteTargetMenuSection
import org.koin.compose.koinInject

@Composable
fun LogcatScreen(
    modifier: Modifier = Modifier,
    onOpenRemoteControl: () -> Unit,
    remoteControl: RemoteControlManager = koinInject(),
    viewModel: LogcatScreenViewModel = viewModel {
        LogcatScreenViewModel(remoteControl = remoteControl)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val terminalMode by DataStore.logTerminalMode.collectAsStateWithLifecycle()
    val session by remoteControl.session.collectAsStateWithLifecycle()
    val connected by remoteControl.targetConnected.collectAsStateWithLifecycle()
    val subscribeLogs = session == null || connected
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current
    var isStarted by remember { mutableStateOf(false) }
    LifecycleStartEffect(viewModel) {
        isStarted = true
        onStopOrDispose {
            isStarted = false
            viewModel.stop()
        }
    }
    LaunchedEffect(isStarted, session?.server?.id, subscribeLogs) {
        if (isStarted) {
            viewModel.initialize(subscribeLogs)
        }
    }
    LogcatContent(
        uiState =
            LogcatPresentation(
                uiState.pause,
                uiState.searchQuery,
                uiState.logLevel,
                uiState.logs,
                uiState.errorMessage,
                uiState.connecting,
            ),
        searchTextFieldState = viewModel.searchTextFieldState,
        levels = logLevels,
        levelLabel = { it.toString() },
        onClearSearch = viewModel::clearSearchQuery,
        onTogglePause = viewModel::togglePause,
        onClearLog = { viewModel.clearLog() },
        onSetLogLevel = viewModel::setLogLevel,
        buildExportContent = { viewModel.buildExportLog().content },
        targetMenu = { dismiss -> RemoteTargetMenuSection(0, 3, onOpenRemoteControl, dismiss) },
        remoteBanner = { RemoteSessionBanner(remoteControl = remoteControl) },
        shareAction = {
            ShareActionRow(scope, viewModel::buildExportLog) {
                snackbar.show(StringOrRes.Direct(it.readableMessage))
            }
        },
        terminalMode = terminalMode,
        modifier = modifier,
    )
}
