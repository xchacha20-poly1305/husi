package fr.husi.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import fr.husi.platform.PlatformInfo
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKeysBar
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyProgressIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState

@Composable
fun TerminalPane(
    session: GhosttyTerminalSession,
    state: GhosttyTerminalState,
    onFinishedSessionKey: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Column(modifier = modifier) {
        GhosttyProgressIndicator(session, Modifier.fillMaxWidth())
        Box(modifier = Modifier.weight(1f)) {
            GhosttyTerminal(
                session = session,
                modifier = Modifier.fillMaxSize(),
                state = state,
                darkColorScheme = isAppThemeDark(),
                onFinishedSessionKey = onFinishedSessionKey,
            )
            overlay()
        }
        if (PlatformInfo.isAndroid) {
            GhosttyExtraKeysBar(state, Modifier.fillMaxWidth())
        }
    }
}

/** Follows the app theme rather than the system one, which the app may override. */
@Composable
fun isAppThemeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
