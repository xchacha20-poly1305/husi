package fr.husi.tailscale

sealed interface TailscaleUiEvent {
    data class Message(val message: String) : TailscaleUiEvent
}
