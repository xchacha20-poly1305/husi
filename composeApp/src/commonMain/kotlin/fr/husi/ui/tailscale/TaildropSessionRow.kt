package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Text
import fr.husi.ktx.formatBytes
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.close
import fr.husi.resources.done
import fr.husi.resources.error
import fr.husi.resources.file_upload
import fr.husi.resources.taildrop_dismiss
import fr.husi.resources.taildrop_to
import fr.husi.tailscale.TaildropSendSession
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** Null while the size is unknown and the send is still running, so the bar stays indeterminate. */
internal fun TaildropSendSession.progressFraction(): Float? {
    val known = files.filter { file -> file.size > 0L }
    if (known.isEmpty()) return if (finished) 1f else null
    val total = known.sumOf { file -> file.size }
    if (total <= 0L) return if (finished) 1f else null
    val sent = known.sumOf { file -> file.sentBytes.coerceAtMost(file.size) }
    return (sent.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** "sent / total" while running, the total once finished, or null when no size is known. */
private fun TaildropSendSession.progressText(): String? {
    val total = files.sumOf { file -> file.size.coerceAtLeast(0L) }
    if (total <= 0L) return null
    if (finished) return total.formatBytes()
    val sent = files.sumOf { file -> file.sentBytes.coerceIn(0L, file.size.coerceAtLeast(0L)) }
    return "${sent.formatBytes()} / ${total.formatBytes()}"
}

@Composable
internal fun TaildropSendSessionRow(
    session: TaildropSendSession,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val failed = session.errorMessage != null
    val summary = listOfNotNull(
        stringResource(Res.string.taildrop_to, session.peerName),
        session.progressText(),
    ).joinToString(separator = " · ")
    Preference(
        title = {
            Text(
                text = session.files.joinToString(separator = ", ") { file -> file.name },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        summary = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(summary)
                session.errorMessage?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
                if (!session.finished) {
                    val fraction = session.progressFraction()
                    if (fraction == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        icon = {
            when {
                failed -> MaskedIcon(resource = Res.drawable.error, color = IconMaskColors.IconCoral)
                session.finished -> MaskedIcon(resource = Res.drawable.done, color = IconMaskColors.IconLightGreen)
                else -> MaskedIcon(resource = Res.drawable.file_upload, color = IconMaskColors.IconLightBlue)
            }
        },
        widgetContainer = {
            SimpleIconButton(
                imageVector = vectorResource(Res.drawable.close),
                contentDescription = stringResource(
                    if (session.finished) Res.string.taildrop_dismiss else Res.string.cancel,
                ),
                onClick = if (session.finished) onDismiss else onCancel,
            )
        },
    )
}
