package fr.husi.ui.tailscale

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.husi.ktx.DisplayTime
import fr.husi.resources.Res
import fr.husi.resources.connected
import fr.husi.resources.tailscale_expired
import fr.husi.resources.tailscale_expires_relative
import fr.husi.resources.tailscale_key_expiry_disabled
import fr.husi.resources.tailscale_state_needs_login
import fr.husi.resources.tailscale_state_stopped
import fr.husi.tailscale.TAILSCALE_STATE_NEEDS_LOGIN
import fr.husi.tailscale.TAILSCALE_STATE_RUNNING
import fr.husi.tailscale.TAILSCALE_STATE_STOPPED
import fr.husi.tailscale.TailscaleEndpointState
import fr.husi.tailscale.TailscalePeerState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun tailscaleStateColor(state: String): Color = when (state) {
    TAILSCALE_STATE_RUNNING -> MaterialTheme.colorScheme.primary
    TAILSCALE_STATE_NEEDS_LOGIN -> MaterialTheme.colorScheme.tertiary
    TAILSCALE_STATE_STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.error
}

@Composable
internal fun tailscaleStateLabel(endpoint: TailscaleEndpointState): String {
    if (endpoint.stateText.isNotEmpty()) return endpoint.stateText
    val resource = when (endpoint.backendState) {
        TAILSCALE_STATE_RUNNING -> Res.string.connected
        TAILSCALE_STATE_NEEDS_LOGIN -> Res.string.tailscale_state_needs_login
        TAILSCALE_STATE_STOPPED -> Res.string.tailscale_state_stopped
        else -> null
    }
    return resource?.let { stringResource(it) } ?: endpoint.backendState
}

@Composable
internal fun tailscaleKeyExpiryText(peer: TailscalePeerState): String {
    if (peer.expired) return stringResource(Res.string.tailscale_expired)
    if (peer.keyExpiry <= 0L) return stringResource(Res.string.tailscale_key_expiry_disabled)
    val whenText = DisplayTime.dateTime(Instant.fromEpochSeconds(peer.keyExpiry))
    return stringResource(Res.string.tailscale_expires_relative, whenText)
}

internal fun tailscaleSeenText(epochSeconds: Long): String =
    DisplayTime.dateTime(Instant.fromEpochSeconds(epochSeconds))
