package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.DropDownSelector
import fr.husi.compose.material3.Text
import fr.husi.core.remote.RemoteControlManager
import fr.husi.ktx.Logs
import fr.husi.resources.Res
import fr.husi.resources.outbound
import fr.husi.resources.outbound_default
import org.jetbrains.compose.resources.stringResource
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

    val defaultLabel = stringResource(Res.string.outbound_default)
    DropDownSelector(
        modifier = modifier,
        label = { Text(stringResource(Res.string.outbound)) },
        value = selectedTag,
        values = tags,
        onValueChange = onSelect,
        displayValue = { tag -> tag.ifEmpty { defaultLabel } },
    )
}
