package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import fr.husi.core.remote.RemoteControlManager
import fr.husi.ktx.Logs
import kotlinx.coroutines.CancellationException
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var tags by remember { mutableStateOf(listOf(DEFAULT_OUTBOUND_TAG)) }
    LaunchedEffect(lifecycleOwner, coreClient) {
        tags = listOf(DEFAULT_OUTBOUND_TAG)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                coreClient.subscribeOutbounds().collect { list ->
                    tags = listOf(DEFAULT_OUTBOUND_TAG) + list.outbounds.map { it.tag }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("subscribe outbounds", e)
            }
        }
    }

    OutboundSelector(selectedTag, onSelect, tags, modifier)
}
