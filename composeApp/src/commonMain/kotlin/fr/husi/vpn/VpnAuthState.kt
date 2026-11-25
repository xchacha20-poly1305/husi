package fr.husi.vpn

const val VPN_ENDPOINT_STATE_CONNECTING = "connecting"
const val VPN_ENDPOINT_STATE_AUTH_PENDING = "auth-pending"
const val VPN_ENDPOINT_STATE_CONNECTED = "connected"
const val VPN_ENDPOINT_STATE_ERROR = "error"

const val OPENCONNECT_STATE_CONNECTING = VPN_ENDPOINT_STATE_CONNECTING
const val OPENCONNECT_STATE_AUTH_PENDING = VPN_ENDPOINT_STATE_AUTH_PENDING
const val OPENCONNECT_STATE_CONNECTED = VPN_ENDPOINT_STATE_CONNECTED
const val OPENCONNECT_STATE_ERROR = VPN_ENDPOINT_STATE_ERROR

const val OPENVPN_STATE_CONNECTING = VPN_ENDPOINT_STATE_CONNECTING
const val OPENVPN_STATE_AUTH_PENDING = VPN_ENDPOINT_STATE_AUTH_PENDING
const val OPENVPN_STATE_CONNECTED = VPN_ENDPOINT_STATE_CONNECTED
const val OPENVPN_STATE_ERROR = VPN_ENDPOINT_STATE_ERROR

data class PendingVpnAuth<C>(
    val endpointTag: String,
    val challenge: C,
)

data class VpnAuthPendingNotice(
    val endpointTag: String,
    val challengeId: String,
)

fun vpnAuthChallengeKey(endpointTag: String, challengeId: String): String = "$endpointTag\n$challengeId"

fun <T> firstVpnAuthPending(
    endpoints: Iterable<T>,
    state: (T) -> String,
    challengeId: (T) -> String?,
    tag: (T) -> String,
): VpnAuthPendingNotice? {
    for (endpoint in endpoints) {
        if (state(endpoint) != VPN_ENDPOINT_STATE_AUTH_PENDING) continue
        val id = challengeId(endpoint) ?: continue
        return VpnAuthPendingNotice(tag(endpoint), id)
    }
    return null
}
