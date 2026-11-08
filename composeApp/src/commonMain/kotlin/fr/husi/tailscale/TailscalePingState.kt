package fr.husi.tailscale

const val TAILSCALE_PING_HISTORY_SIZE = 30

data class TailscalePingState(
    val running: Boolean = false,
    val hasResult: Boolean = false,
    val latencyMilliseconds: Double = 0.0,
    val direct: Boolean = false,
    val derpRegionCode: String = "",
    val endpoint: String = "",
    val peerRelay: String = "",
    val error: String = "",
    val latencyHistory: List<Float> = emptyList(),
)

data class TailscalePeerUiState(
    val endpoint: TailscaleEndpointState? = null,
    val peer: TailscalePeerState? = null,
    val ping: TailscalePingState = TailscalePingState(),
)
