package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.core.remote.RemoteControlManager
import fr.husi.ktx.Logs
import org.koin.compose.koinInject

private const val DEFAULT_OUTBOUND_TAG = ""

@Composable
internal fun OutboundSelector(
    selectedTag: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    remoteControl: RemoteControlManager = koinInject(),
) {
    val coreClient by remoteControl.activeClient.collectAsStateWithLifecycle()
    var tags by remember { mutableStateOf(listOf(DEFAULT_OUTBOUND_TAG)) }
    LaunchedEffect(coreClient) {
        tags = listOf(DEFAULT_OUTBOUND_TAG)
        try {
            coreClient.subscribeOutbounds().collect { list ->
                tags = listOf(DEFAULT_OUTBOUND_TAG) + list.outboundsList.map { it.tag }
            }
        } catch (e: Exception) {
            Logs.w("subscribe outbounds", e)
        }
    }

    OutboundSelector(selectedTag, onSelect, tags, modifier)
}
