package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.Window
import androidx.compose.ui.window.v2.rememberWindowStateWithBounds
import fr.husi.compose.theme.AppTheme
import fr.husi.resources.Res
import fr.husi.resources.ic_service_active
import fr.husi.resources.tailscale_ssh
import fr.husi.tailscale.TailscaleSSHSessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal object TailscaleSSHWindowState {
    val visible = MutableStateFlow(false)

    val focusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun show() {
        if (visible.value) {
            focusRequests.tryEmit(Unit)
        } else {
            visible.value = true
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TailscaleSSHWindow(manager: TailscaleSSHSessionManager = koinInject()) {
    val visible by TailscaleSSHWindowState.visible.collectAsState()
    if (!visible) return
    val close = {
        TailscaleSSHWindowState.visible.value = false
        manager.closeAll()
    }
    val windowState = rememberWindowStateWithBounds(initialSize = DpSize(800.dp, 520.dp))
    Window(
        onCloseRequest = close,
        state = windowState,
        title = stringResource(Res.string.tailscale_ssh),
        icon = painterResource(Res.drawable.ic_service_active),
    ) {
        LaunchedEffect(Unit) {
            TailscaleSSHWindowState.focusRequests.collect {
                windowState.requestMinimized(false)
                window.toFront()
            }
        }
        AppTheme {
            TailscaleSSHTerminalScreen(onClose = close)
        }
    }
}

