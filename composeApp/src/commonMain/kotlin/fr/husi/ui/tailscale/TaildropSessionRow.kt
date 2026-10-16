package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.taildrop_dismiss
import fr.husi.resources.taildrop_to
import fr.husi.tailscale.TaildropSendSession
import org.jetbrains.compose.resources.stringResource

/** Null while the size is unknown and the send is still running, so the bar stays indeterminate. */
internal fun TaildropSendSession.progressFraction(): Float? {
    val known = files.filter { file -> file.size > 0L }
    if (known.isEmpty()) return if (finished) 1f else null
    val total = known.sumOf { file -> file.size }
    if (total <= 0L) return if (finished) 1f else null
    val sent = known.sumOf { file -> file.sentBytes.coerceAtMost(file.size) }
    return (sent.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
}

@Composable
internal fun TaildropSendSessionRow(
    session: TaildropSendSession,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = session.files.joinToString(separator = ", ") { file -> file.name },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(Res.string.taildrop_to, session.peerName),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        val fraction = session.progressFraction()
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        session.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (session.finished) {
            TextButton(text = stringResource(Res.string.taildrop_dismiss), onClick = onDismiss)
        } else {
            TextButton(text = stringResource(Res.string.cancel), onClick = onCancel)
        }
    }
}
