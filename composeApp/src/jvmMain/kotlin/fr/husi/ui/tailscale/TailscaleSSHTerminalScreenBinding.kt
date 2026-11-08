package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.tailscale.TailscaleSSHSessionManager
import org.koin.compose.koinInject

@Composable
fun TailscaleSSHTerminalScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    manager: TailscaleSSHSessionManager = koinInject(),
) {
    val state by manager.state.collectAsStateWithLifecycle()
    TailscaleSSHTerminalContent(
        sessions = state.sessions,
        activeSessionId = state.activeSessionId,
        onClose = onClose,
        onCloseSession = manager::close,
        onSelectSession = manager::select,
        onDuplicateSession = { manager.duplicate(it) },
        modifier = modifier,
    )
}
