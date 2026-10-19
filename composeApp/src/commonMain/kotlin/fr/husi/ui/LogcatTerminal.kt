package fr.husi.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.proto.daemon.LogLevel
import fr.husi.ui.terminal.isAppThemeDark
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState

private const val LOGCAT_TERMINAL_SCROLLBACK_LINES = 20_000L

@Composable
internal fun rememberLogcatTerminalSession(): GhosttyTerminalSession {
    val session = remember {
        GhosttyTerminalSession(
            GhosttyTerminalSession.Options(
                maxScrollbackLines = LOGCAT_TERMINAL_SCROLLBACK_LINES,
            ),
        )
    }
    DisposableEffect(session) {
        onDispose { session.close() }
    }
    return session
}

@Composable
internal fun rememberLogcatTerminalState(): GhosttyTerminalState = remember {
    GhosttyTerminalState().apply {
        showImeOnTap = false
        showJumpToBottom = false
    }
}

@Composable
internal fun LogcatTerminal(
    session: GhosttyTerminalSession,
    state: GhosttyTerminalState,
    logs: List<LogEntry>,
    logLevel: LogLevel,
    searchQuery: String?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val writer = remember(session) { LogcatTerminalWriter() }
    LaunchedEffect(session, logs, logLevel, searchQuery) {
        writer.update(logs, logLevel, searchQuery)?.let { session.feedOutput(it.encodeToByteArray()) }
    }
    GhosttyTerminal(
        session = session,
        modifier = modifier,
        state = state,
        darkColorScheme = isAppThemeDark(),
        focusOnAttach = false,
        contentPadding = contentPadding,
    )
}

