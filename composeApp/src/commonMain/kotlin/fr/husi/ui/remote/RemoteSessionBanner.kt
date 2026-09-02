package fr.husi.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.CapsuleDefaults
import fr.husi.compose.CapsuleSurface
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.core.remote.RemoteControlManager
import fr.husi.core.remote.RemoteSession
import fr.husi.core.remote.RemoteSessionState
import fr.husi.ktx.blankAsNull
import fr.husi.resources.Res
import fr.husi.resources.cast_connected
import fr.husi.resources.computer_cancel
import fr.husi.resources.remote_disconnect
import fr.husi.resources.remote_uptime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

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
                session = current,
                windowInsets = windowInsets,
                onDisconnect = { remoteControl.exitRemote() },
            )
        }
    }
}

@Composable
private fun RemoteSessionBannerContent(
    session: RemoteSession,
    windowInsets: WindowInsets,
    onDisconnect: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.startedAt, session.state) {
        if (session.state != RemoteSessionState.CONNECTED || session.startedAt == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1.seconds)
        }
    }
    val accentColor = sessionAccentColor(session.state)
    CapsuleSurface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(
                horizontal = CapsuleDefaults.HorizontalPadding,
                vertical = BannerVerticalPadding,
            ),
        borderColor = accentColor.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CapsuleDefaults.HorizontalPadding, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CapsuleDefaults.Spacing),
        ) {
            SessionStateIndicator(state = session.state, color = accentColor)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = CapsuleDefaults.VerticalPadding),
            ) {
                Text(
                    text = serverDisplayName(session.server),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = bannerStatusText(session, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SimpleIconButton(
                imageVector = vectorResource(Res.drawable.computer_cancel),
                contentDescription = stringResource(Res.string.remote_disconnect),
                onClick = { scope.launch { onDisconnect() } },
            )
        }
    }
}

@Composable
private fun SessionStateIndicator(state: RemoteSessionState, color: Color) {
    if (state == RemoteSessionState.CONNECTED) {
        Icon(
            imageVector = vectorResource(Res.drawable.cast_connected),
            contentDescription = null,
            modifier = Modifier.size(IndicatorSize),
            tint = color,
        )
    } else {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(IndicatorSize),
            color = color,
        )
    }
}

@Composable
private fun sessionAccentColor(state: RemoteSessionState): Color = when (state) {
    RemoteSessionState.CONNECTED -> MaterialTheme.colorScheme.primary
    RemoteSessionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
    RemoteSessionState.RECONNECTING -> MaterialTheme.colorScheme.error
}

@Composable
private fun bannerStatusText(session: RemoteSession, now: Long): String {
    val status = remoteSessionStatusText(session.state)
    val startedAt = session.startedAt
    if (session.state == RemoteSessionState.CONNECTED && startedAt != null && startedAt > 0L) {
        return "$status · ${stringResource(Res.string.remote_uptime, formatUptime(startedAt, now))}"
    }
    return session.lastError.blankAsNull()?.let { "$status · $it" } ?: status
}

internal fun formatUptime(startedAtMillis: Long, nowMillis: Long): String {
    val totalSeconds = ((nowMillis - startedAtMillis) / 1000L).fastCoerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
