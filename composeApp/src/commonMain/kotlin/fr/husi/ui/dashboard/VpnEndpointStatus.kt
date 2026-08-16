package fr.husi.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.auth_required
import fr.husi.resources.connected
import fr.husi.resources.connecting
import fr.husi.resources.error_title
import fr.husi.vpn.VPN_ENDPOINT_STATE_AUTH_PENDING
import fr.husi.vpn.VPN_ENDPOINT_STATE_CONNECTED
import fr.husi.vpn.VPN_ENDPOINT_STATE_ERROR
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun <T> VpnEndpointStatusSection(
    title: String,
    endpoints: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(endpoint: T, showTag: Boolean) -> Unit,
) {
    if (endpoints.isEmpty()) return

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            for ((index, endpoint) in endpoints.withIndex()) {
                if (index > 0) HorizontalDivider()
                content(endpoint, endpoints.size > 1)
            }
        }
    }
}

@Composable
internal fun VpnStatusInfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.padding(end = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = color,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun vpnAuthStateText(state: String, stateText: String = ""): String {
    if (stateText.isNotEmpty()) return stateText
    return stringResource(
        when (state) {
            VPN_ENDPOINT_STATE_CONNECTED -> Res.string.connected
            VPN_ENDPOINT_STATE_AUTH_PENDING -> Res.string.auth_required
            VPN_ENDPOINT_STATE_ERROR -> Res.string.error_title
            else -> Res.string.connecting
        },
    )
}

@Composable
internal fun vpnAuthStateColor(state: String): Color = when (state) {
    VPN_ENDPOINT_STATE_CONNECTED -> MaterialTheme.colorScheme.primary
    VPN_ENDPOINT_STATE_AUTH_PENDING -> MaterialTheme.colorScheme.tertiary
    VPN_ENDPOINT_STATE_ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
