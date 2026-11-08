package fr.husi.tailscale

import kotlinx.serialization.Serializable

const val DEFAULT_SSH_USERNAME = "root"
const val DEFAULT_SSH_TERMINAL_TYPE = "xterm-256color"

@Serializable
data class TailscaleSSHLogin(
    val username: String = DEFAULT_SSH_USERNAME,
    val terminalType: String = DEFAULT_SSH_TERMINAL_TYPE,
)

