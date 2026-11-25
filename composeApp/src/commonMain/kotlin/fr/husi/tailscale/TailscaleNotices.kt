package fr.husi.tailscale

import fr.husi.vpn.vpnAuthChallengeKey

data class TailscaleNoticeSnapshot(
    val unreadFileCount: Int,
    val authUrl: String,
    val needsLogin: Boolean,
)

sealed interface TailscaleNotice {
    val endpointTag: String

    /** [count] is how many unread files arrived since the previous snapshot. */
    data class FilesReceived(
        override val endpointTag: String,
        val count: Int,
    ) : TailscaleNotice

    data class NeedsLogin(
        override val endpointTag: String,
    ) : TailscaleNotice
}

fun TailscaleEndpointState.toNoticeSnapshot() = TailscaleNoticeSnapshot(
    unreadFileCount = unreadFileCount,
    authUrl = authUrl,
    needsLogin = backendState == TAILSCALE_STATE_NEEDS_LOGIN,
)

fun List<TailscaleEndpointState>.toNoticeSnapshots(): Map<String, TailscaleNoticeSnapshot> =
    associate { endpoint -> endpoint.endpointTag to endpoint.toNoticeSnapshot() }

private fun taildropUnreadNoticeKey(endpointTag: String) = "$endpointTag\ntaildrop"

fun List<TailscaleEndpointState>.dashboardNoticeCounts(): Map<String, Int> = buildMap {
    for (endpoint in this@dashboardNoticeCounts) {
        val waitingForLogin = endpoint.backendState == TAILSCALE_STATE_NEEDS_LOGIN &&
            endpoint.authUrl.isNotEmpty()
        if (waitingForLogin) {
            put(vpnAuthChallengeKey(endpoint.endpointTag, endpoint.authUrl), 1)
        }
        if (endpoint.unreadFileCount > 0) {
            put(taildropUnreadNoticeKey(endpoint.endpointTag), endpoint.unreadFileCount)
        }
    }
}

fun tailscaleNotices(
    previous: Map<String, TailscaleNoticeSnapshot>,
    current: List<TailscaleEndpointState>,
): List<TailscaleNotice> {
    val notices = mutableListOf<TailscaleNotice>()
    for (endpoint in current) {
        val prior = previous[endpoint.endpointTag]
        if (prior != null) {
            val unreadIncrease = endpoint.unreadFileCount - prior.unreadFileCount
            if (unreadIncrease > 0) {
                notices += TailscaleNotice.FilesReceived(endpoint.endpointTag, unreadIncrease)
            }
        }
        val needsLogin = endpoint.backendState == TAILSCALE_STATE_NEEDS_LOGIN
        val authUrl = endpoint.authUrl
        val loginBecameVisible = needsLogin && authUrl.isNotEmpty() &&
            (prior == null || !prior.needsLogin || authUrl != prior.authUrl)
        if (loginBecameVisible) {
            notices += TailscaleNotice.NeedsLogin(endpoint.endpointTag)
        }
    }
    return notices
}
