package fr.husi.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.state
import fr.husi.resources.tailscale
import fr.husi.resources.tailscale_unread_count
import fr.husi.tailscale.TailscaleEndpointState
import fr.husi.ui.tailscale.tailscaleStateColor
import fr.husi.ui.tailscale.tailscaleStateLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TailscaleStatusSection(
    endpoints: List<TailscaleEndpointState>,
    onOpenEndpoint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (endpoints.isEmpty()) return

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        // The rows carry their own inner padding, so the hover highlight stays rounded
        // and the content lines up with the card title.
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.tailscale),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
            for ((index, endpoint) in endpoints.withIndex()) {
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                }
                EndpointRow(endpoint = endpoint, onOpenEndpoint = onOpenEndpoint)
            }
        }
    }
}

@Composable
private fun EndpointRow(
    endpoint: TailscaleEndpointState,
    onOpenEndpoint: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpenEndpoint(endpoint.endpointTag) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(endpoint.endpointTag, style = MaterialTheme.typography.titleMedium)
        VpnStatusInfoRow(
            label = stringResource(Res.string.state),
            value = tailscaleStateLabel(endpoint),
            color = tailscaleStateColor(endpoint.backendState),
        )
        if (endpoint.unreadFileCount > 0) {
            Text(
                text = pluralStringResource(
                    Res.plurals.tailscale_unread_count,
                    endpoint.unreadFileCount,
                    endpoint.unreadFileCount,
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
