@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import fr.husi.compose.SheetActionRow
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.database.DataStore
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.action_export
import fr.husi.ui.remote.RemoteBanner
import fr.husi.ui.remote.RemoteMenu
import kotlin.io.encoding.Base64
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RemoteLogBinding() {
    val remote = koinInject<RemoteState>()
    val model = koinViewModel<RemoteLogViewModel>()
    val terminalMode by DataStore.logTerminalMode.collectAsStateWithLifecycle()
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
                stringResource(Res.string.action_export),
                onClick = {
                    saveFile("husi.log", Base64.encode(model.export().encodeToByteArray()))
                },
            )
        },
        terminalMode = terminalMode && remote.terminalReady,
    )
}

@JsFun("(name,data) => husiRpc.saveFile(name,data)")
private external fun saveFile(name: String, base64: String)
