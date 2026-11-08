package fr.husi.tailscale


data class TailscalePeerState(
    val id: String,
    val stableId: String,
    val hostName: String,
    val dnsName: String,
    val operatingSystem: String,
    val tailscaleIps: List<String>,
    val sshHostKeys: List<String>,
    val online: Boolean,
    val exitNode: Boolean,
    val exitNodeOption: Boolean,
    val shareeNode: Boolean,
    val expired: Boolean,
    val active: Boolean,
    val receivedBytes: Long,
    val transmittedBytes: Long,
    val keyExpiry: Long,
    val lastSeen: Long,
    val canReceiveFiles: Boolean,
) {
    /** The first DNS label, or [hostName] when the peer has no DNS name. */
    val displayName: String
        get() = dnsName.substringBefore('.').ifEmpty { hostName }
}

data class TailscaleUserGroupState(
    val userId: Long,
    val loginName: String,
    val displayName: String,
    val profilePictureUrl: String,
    val peers: List<TailscalePeerState>,
)

data class TailscaleEndpointState(
    val endpointTag: String,
    val backendState: String,
    val stateText: String,
    val authUrl: String,
    val networkName: String,
    val magicDnsSuffix: String,
    val selfPeer: TailscalePeerState?,
    val exitNode: TailscalePeerState?,
    val userGroups: List<TailscaleUserGroupState>,
    val keyAuth: Boolean,
    val canShareFiles: Boolean,
    val waitingFileCount: Int,
    val receivingFileCount: Int,
    val unreadFileCount: Int,
) {
    val taildropFileCount: Int
        get() = waitingFileCount + receivingFileCount

    val hasExitNodeCandidates: Boolean
        get() = exitNode != null || exitNodeCandidates().isNotEmpty()

    /** Peers that can be selected as an exit node, excluding this device. */
    fun exitNodeCandidates(): List<TailscalePeerState> {
        val selfStableId = selfPeer?.stableId
        return userGroups.flatMap { group ->
            group.peers.filter { peer -> peer.exitNodeOption && peer.stableId != selfStableId }
        }
    }

    fun findPeer(peerId: String): TailscalePeerState? {
        if (selfPeer?.id == peerId) return selfPeer
        for (group in userGroups) {
            val peer = group.peers.firstOrNull { it.id == peerId }
            if (peer != null) return peer
        }
        return null
    }

    fun tailDropTargets(): List<TailscalePeerState> {
        val selfId = selfPeer?.id
        val peers = userGroups.flatMap { group -> group.peers }
            .filter { peer -> peer.canReceiveFiles && peer.id != selfId }
        return peers.sortedWith { left, right ->
            val onlineOrder = right.online.compareTo(left.online)
            if (onlineOrder != 0) {
                onlineOrder
            } else {
                left.displayName.compareTo(right.displayName)
            }
        }
    }
}

const val TAILSCALE_STATE_RUNNING = "Running"
const val TAILSCALE_STATE_NEEDS_LOGIN = "NeedsLogin"
const val TAILSCALE_STATE_STOPPED = "Stopped"
