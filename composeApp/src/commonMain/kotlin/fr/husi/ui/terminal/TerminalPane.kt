package fr.husi.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import fr.husi.platform.PlatformInfo
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyContentPaddingMode
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyJumpToBottomButton
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyProgressIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyScrollIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState

/**
 * The terminal fills the whole pane and is the haze source of [hazeState], so the topbar above it and the extra keys
 * toolbar floating over its bottom are translucent. Its rows fit between [contentPadding] and the toolbar, so a
 * full-screen program such as vim sees all of its screen.
 */
@Composable
fun TerminalPane(
    session: GhosttyTerminalSession,
    state: GhosttyTerminalState,
    onFinishedSessionKey: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val showExtraKeys = PlatformInfo.isAndroid
    var extraKeysHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val bottomInset = contentPadding.calculateBottomPadding()
    val extraKeysClearance = if (showExtraKeys) {
        with(density) { extraKeysHeightPx.toDp() } + ExtraKeysToolbarSpacing * 2
    } else {
        0.dp
    }
    val terminalPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = bottomInset + extraKeysClearance,
    )

    Box(modifier = modifier) {
        GhosttyTerminal(
            session = session,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            state = state,
            darkColorScheme = isAppThemeDark(),
            onFinishedSessionKey = onFinishedSessionKey,
            contentPadding = terminalPadding,
            contentPaddingMode = GhosttyContentPaddingMode.INSET,
        )
        GhosttyProgressIndicator(
            session,
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = terminalPadding.calculateTopPadding())
                .fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(terminalPadding),
        ) {
            GhosttyScrollIndicator(
                state,
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
            GhosttyJumpToBottomButton(
                state,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
            overlay()
        }
        if (showExtraKeys) {
            TerminalExtraKeysToolbar(
                state = state,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + ExtraKeysToolbarSpacing)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .onSizeChanged { extraKeysHeightPx = it.height },
            )
        }
    }
}

/** The gap below the extra keys toolbar, and between it and the last terminal row. */
private val ExtraKeysToolbarSpacing = 8.dp

/** Follows the app theme rather than the system one, which the app may override. */
@Composable
fun isAppThemeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
