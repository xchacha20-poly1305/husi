package fr.husi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.proto.daemon.LogLevel
import fr.husi.ui.terminal.isAppThemeDark
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyScrollIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState

private const val LOGCAT_TERMINAL_SCROLLBACK_LINES = 20_000L

@Composable
internal fun rememberLogcatTerminalSession(): GhosttyTerminalSession {
    val session = remember {
        GhosttyTerminalSession(
            GhosttyTerminalSession.Options(
                maxScrollbackLines = LOGCAT_TERMINAL_SCROLLBACK_LINES,
                defaultGraphemeCluster = true,
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
    Box(modifier = modifier) {
        GhosttyTerminal(
            session = session,
            modifier = Modifier.fillMaxSize(),
            state = state,
            darkColorScheme = isAppThemeDark(),
            focusOnAttach = false,
            contentPadding = contentPadding,
        )
        GhosttyScrollIndicator(
            state,
            Modifier
                .align(Alignment.CenterEnd)
                .padding(contentPadding)
                .padding(end = 2.dp),
        )
    }
}

