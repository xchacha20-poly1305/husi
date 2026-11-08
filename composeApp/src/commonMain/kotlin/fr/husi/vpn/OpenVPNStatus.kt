package fr.husi.vpn


/** Answered with a username, a password and a secret. */
const val OPENVPN_CHALLENGE_CREDENTIALS = "credentials"

/** Answered with a secret alone. */
const val OPENVPN_CHALLENGE_SECRET = "secret"

/** Server notice, nothing to answer. */
const val OPENVPN_CHALLENGE_MESSAGE = "message"

/** The user authenticates in a browser; the server reports the outcome. */
const val OPENVPN_CHALLENGE_OPEN_URL = "open-url"

data class OpenVPNChallengeState(
    val id: String,
    val kind: String,
    val username: String,
    val message: String,
    val url: String,
    val secretMessage: String,
    val echo: Boolean,
    val previousError: String,
    /** Unix seconds, zero when the challenge never expires. */
    val deadline: Long,
) {
    /** The kinds a client answers; the others are informational. */
    val answerable: Boolean
        get() = kind == OPENVPN_CHALLENGE_CREDENTIALS || kind == OPENVPN_CHALLENGE_SECRET
}

/** Remaining time until [deadline], or null when the challenge never expires. */
fun formatOpenVPNRemaining(deadline: Long, nowEpochSeconds: Long): String? {
    if (deadline <= 0) return null
    val remaining = (deadline - nowEpochSeconds).coerceAtLeast(0)
    return "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
}

data class OpenVPNTunnelInfoState(
    val server: String,
    val network: String,
    val cipher: String,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val dns: List<String>,
    val mtu: Int,
    /** Unix seconds, zero when unset. */
    val connectedSince: Long,
)

data class OpenVPNEndpointState(
    val tag: String,
    val state: String,
    val stateText: String,
    val error: String,
    val challenge: OpenVPNChallengeState?,
    val tunnelInfo: OpenVPNTunnelInfoState?,
)
