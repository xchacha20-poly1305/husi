package fr.husi.tailscale

import fr.husi.proto.daemon.TailscalePeer
import fr.husi.proto.daemon.TailscaleStatusUpdate

/** Maps a `SubscribeTailscaleStatus` update to the endpoints the Tailscale screens show. */
internal fun TailscaleStatusUpdate.toTailscaleEndpoints(): List<TailscaleEndpointState> =
    endpoints.map { endpoint ->
        TailscaleEndpointState(
            endpointTag = endpoint.endpointTag,
            backendState = endpoint.backendState,
            stateText = endpoint.stateText,
            authUrl = endpoint.authURL,
            networkName = endpoint.networkName,
            magicDnsSuffix = endpoint.magicDNSSuffix,
            selfPeer = endpoint.self?.toTailscalePeer(),
            exitNode = endpoint.exitNode?.toTailscalePeer(),
            userGroups =
                endpoint.userGroups.map { group ->
                    TailscaleUserGroupState(
                        userId = group.userID,
                        loginName = group.loginName,
                        displayName = group.displayName,
                        profilePictureUrl = group.profilePicURL,
                        peers = group.peers.map { it.toTailscalePeer() },
                    )
                },
            keyAuth = endpoint.keyAuth,
            canShareFiles = endpoint.canShareFiles,
            waitingFileCount = endpoint.waitingFileCount,
            receivingFileCount = endpoint.receivingFileCount,
            unreadFileCount = endpoint.unreadFileCount,
        )
    }

private fun TailscalePeer.toTailscalePeer(): TailscalePeerState {
    val dns = dnsName.removeSuffix(".")
    return TailscalePeerState(
        id = dns.ifEmpty { hostName },
        stableId = stableID,
        hostName = hostName,
        dnsName = dns,
        operatingSystem = os,
        tailscaleIps = tailscaleIPs,
        sshHostKeys = sshHostKeys,
        online = online,
        exitNode = exitNode,
        exitNodeOption = exitNodeOption,
        shareeNode = shareeNode,
        expired = expired,
        active = active,
        receivedBytes = rxBytes,
        transmittedBytes = txBytes,
        keyExpiry = keyExpiry,
        lastSeen = lastSeen,
        canReceiveFiles = canReceiveFiles,
    )
}
