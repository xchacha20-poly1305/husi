package fr.husi.tailscale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.ktx.readableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class TailscaleEndpointViewModel(
    private val endpointTag: String,
    repository: TailscaleStatusRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val endpoint: StateFlow<TailscaleEndpointState?>
        field = MutableStateFlow(repository.snapshot.value.endpoints.findEndpoint())

    val closed: SharedFlow<Unit>
        field = MutableSharedFlow()

    val uiEvent: SharedFlow<TailscaleUiEvent>
        field = MutableSharedFlow()

    private val presence = TailscalePresence.endpoint(endpointTag)

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                endpoint.value = snapshot.endpoints.findEndpoint()
                if (presence.shouldClose(snapshot)) {
                    closed.emit(Unit)
                }
            }
        }
    }

    /** Empty [stableId] clears the exit node. */
    fun setExitNode(stableId: String) = launchAction {
        presence.requireTarget().client.setTailscaleExitNode(endpointTag, stableId)
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiEvent.emit(TailscaleUiEvent.Message(e.readableMessage))
            }
        }
    }

    private fun List<TailscaleEndpointState>.findEndpoint(): TailscaleEndpointState? =
        firstOrNull { it.endpointTag == endpointTag }
}
