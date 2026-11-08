package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_STUN_SERVER = "stun.voipgate.com:3478"

@Immutable
internal data class StunScreenUiState(
    val server: String = DEFAULT_STUN_SERVER,
    /** Empty means the service's default outbound; ignored when it is not running. */
    val outboundTag: String = "",
    val isDoing: Boolean = false,
    val report: StunReport? = null,
)

@Immutable
internal sealed interface StunScreenUiEvent {
    data class Alert(val message: String) : StunScreenUiEvent
}

internal interface StunPresenter {
    val uiState: StateFlow<StunScreenUiState>
    val uiEvent: SharedFlow<StunScreenUiEvent>

    fun setServer(value: String)

    fun setOutboundTag(value: String)

    fun doTest(value: Boolean)

    fun cancel()
}
