package fr.husi.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleStartEffect
import fr.husi.compose.SheetActionRow
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Icon
import fr.husi.database.DataStore
import fr.husi.remote.RemoteState
import fr.husi.remote.RemoteSubscription
import fr.husi.resources.Res
import fr.husi.resources.action_export
import fr.husi.resources.download
import fr.husi.ui.remote.RemoteBanner
import fr.husi.ui.remote.RemoteMenu
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RemoteLogBinding() {
    val scope = rememberCoroutineScope()
    val remote = koinInject<RemoteState>()
    val model = koinViewModel<RemoteLogViewModel>()
    val terminalMode by DataStore.logTerminalMode.collectAsStateWithLifecycle()
    LifecycleStartEffect(remote) {
        val release = remote.retainSubscription(RemoteSubscription.LOGS)
        onStopOrDispose { release() }
    }
    LogcatContent(
        uiState = model.presentation,
        searchTextFieldState = model.searchTextFieldState,
        levels = model.levels,
        levelLabel = { it },
        onClearSearch = model::clearSearch,
        onTogglePause = model::togglePause,
        onClearLog = model::clear,
        onSetLogLevel = { model.level = it },
        buildExportContent = model::export,
        targetMenu = { RemoteMenu(3, it) },
        remoteBanner = { RemoteBanner() },
        shareAction = {
            SheetActionRow(
                text = stringResource(Res.string.action_export),
                leadingIcon = { Icon(vectorResource(Res.drawable.download), null) },
                onClick = {
                    val bytes = model.export().encodeToByteArray()
                    scope.launch {
                        FileKit.download(bytes, "husi.log")
                    }
                },
            )
        },
        terminalMode = terminalMode && remote.terminalReady,
    )
}
