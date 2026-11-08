package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.tailscale.TailscaleEndpointViewModel
import fr.husi.tailscale.TailscaleStatusRepository
import org.koin.compose.koinInject

@Composable
fun TailscaleExitNodeScreen(
    endpointTag: String,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    repository: TailscaleStatusRepository = koinInject(),
    viewModel: TailscaleEndpointViewModel = viewModel(key = endpointTag) {
        TailscaleEndpointViewModel(
            endpointTag = endpointTag,
            repository = repository,
        )
    },
) {
    val endpoint by viewModel.endpoint.collectAsStateWithLifecycle()
    CollectTailscaleEvents(viewModel.uiEvent, viewModel.closed, onBackPress)
    TailscaleExitNodeContent(endpoint, onBackPress, viewModel::setExitNode, modifier)
}
