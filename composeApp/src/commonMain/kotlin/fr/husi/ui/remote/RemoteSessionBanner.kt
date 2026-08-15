package fr.husi.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Text
import fr.husi.core.remote.RemoteControlManager
import fr.husi.core.remote.RemoteSession
import fr.husi.core.remote.RemoteSessionState
import fr.husi.ktx.blankAsNull
import fr.husi.resources.Res
import fr.husi.resources.remote_disconnect
import fr.husi.resources.remote_uptime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

@Composable
fun RemoteSessionBanner(
    modifier: Modifier = Modifier,
    remoteControl: RemoteControlManager = koinInject(),
) {
    val session by remoteControl.session.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = session != null, modifier = modifier) {
        session?.let { current ->
            RemoteSessionBannerContent(
                session = current,
                onDisconnect = { remoteControl.exitRemote() },
            )
        }
    }
}

@Composable
private fun RemoteSessionBannerContent(
    session: RemoteSession,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (session.state != RemoteSessionState.CONNECTED) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = serverDisplayName(session.server),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = bannerStatusText(session, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(stringResource(Res.string.remote_disconnect)) {
                    scope.launch { onDisconnect() }
                }
            }
        }
    }
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
