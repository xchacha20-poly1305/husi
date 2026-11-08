package fr.husi.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.core.remote.RemoteControlManager
import org.koin.compose.koinInject

private val IndicatorSize = 20.dp
private val BannerVerticalPadding = 4.dp

@Composable
fun RemoteSessionBanner(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    remoteControl: RemoteControlManager = koinInject(),
) {
    val session by remoteControl.session.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = session != null, modifier = modifier) {
        session?.let { current ->
            RemoteSessionBannerContent(
                session =
                    RemoteSessionInfo(
                        serverDisplayName(current.server),
                        current.state,
                        current.startedAt,
                    ),
                windowInsets = windowInsets,
                onDisconnect = { remoteControl.exitRemote() },
            )
        }
    }
}
