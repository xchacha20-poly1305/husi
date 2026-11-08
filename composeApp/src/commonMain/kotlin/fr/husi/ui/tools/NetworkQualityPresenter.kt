package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import fr.husi.NETWORK_QUALITY_CONFIG_URL
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@Immutable
internal data class NetworkQualityScreenUiState(
    val configUrl: String = NETWORK_QUALITY_CONFIG_URL,
    val urlError: StringOrRes? = null,
    val serial: Boolean = false,
    val maxRuntimeSeconds: Int = DEFAULT_MAX_RUNTIME_SECONDS,
    val maxRuntimeError: StringOrRes? = null,
    val http3: Boolean = false,
    val outboundTag: String = "",
    val canTest: Boolean = true,
    val report: NetworkQualityReport? = null,
)

@Immutable
internal sealed interface NetworkQualityScreenUiEvent {
    class Snackbar(val message: StringOrRes) : NetworkQualityScreenUiEvent

    class ErrorAlert(val message: StringOrRes) : NetworkQualityScreenUiEvent
}

private const val DEFAULT_MAX_RUNTIME_SECONDS = 30

internal interface NetworkQualityPresenter {
    val uiState: StateFlow<NetworkQualityScreenUiState>
    val uiEvent: SharedFlow<NetworkQualityScreenUiEvent>

    fun setConfigUrl(value: String?)

    fun setSerial(value: Boolean)

    fun setHttp3(value: Boolean)

    fun setMaxRuntimeSeconds(value: String?)

    fun setOutboundTag(value: String)

    fun doTest(value: Boolean)

    fun cancel()
}
