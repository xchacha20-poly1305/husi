package fr.husi.tailscale

import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import kotlinx.coroutines.flow.StateFlow

data class TailscaleSSHTarget(
    val endpointTag: String,
    val peerStableId: String,
    val peerHostName: String,
    val peerAddress: String,
    val hostKeys: List<String>,
    val login: TailscaleSSHLogin,
)

enum class TailscaleSSHPhase { CONNECTING, RUNNING, FINISHED }

interface TailscaleSSHSessionView {
    val id: Long
    val target: TailscaleSSHTarget
    val terminal: GhosttyTerminalSession
    val phase: StateFlow<TailscaleSSHPhase>
    val banner: StateFlow<String?>
}
