package fr.husi.ui.log

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.proto.daemon.LogLevel
import fr.husi.ui.terminal.isAppThemeDark
import io.github.oikvpqya.compose.fastscroller.ScrollbarAdapter
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState
import kotlin.math.roundToLong

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
    GhosttyTerminal(
        session = session,
        modifier = modifier,
        state = state,
        darkColorScheme = isAppThemeDark(),
        focusOnAttach = false,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun rememberLogcatTerminalScrollbarAdapter(state: GhosttyTerminalState): ScrollbarAdapter =
    remember(state) { TerminalScrollbarAdapter(state) }

/** Measures the scrollback in rows, so dragging the thumb moves the terminal row by row. */
private class TerminalScrollbarAdapter(private val state: GhosttyTerminalState) : ScrollbarAdapter {
    override val scrollOffset: Double
        get() = state.scrollbar.offset.toDouble()

    override val contentSize: Double
        get() = state.scrollbar.total.toDouble()

    override val viewportSize: Double
        get() = state.scrollbar.length.toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        state.scrollTo(scrollOffset.roundToLong())
    }
}
