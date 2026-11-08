package fr.husi.tailscale

import fr.husi.remote.flag
import fr.husi.remote.items
import fr.husi.remote.number
import fr.husi.remote.obj
import fr.husi.remote.strings
import fr.husi.remote.text
import kotlinx.serialization.json.JsonObject

/** Maps a `SubscribeTailscaleStatus` update to the endpoints the Tailscale screens show. */
internal fun JsonObject.toTailscaleEndpoints(): List<TailscaleEndpointState> =
    items("endpoints").map { endpoint ->
        TailscaleEndpointState(
            endpointTag = endpoint.text("endpointTag"),
            backendState = endpoint.text("backendState"),
            stateText = endpoint.text("stateText"),
            authUrl = endpoint.text("authURL"),
            networkName = endpoint.text("networkName"),
            magicDnsSuffix = endpoint.text("magicDNSSuffix"),
            selfPeer = endpoint.obj("self").takeIf { it.isNotEmpty() }?.toTailscalePeer(),
            exitNode = endpoint.obj("exitNode").takeIf { it.isNotEmpty() }?.toTailscalePeer(),
            userGroups =
                endpoint.items("userGroups").map { group ->
                    TailscaleUserGroupState(
                        userId = group.number("userID"),
                        loginName = group.text("loginName"),
                        displayName = group.text("displayName"),
                        profilePictureUrl = group.text("profilePicURL"),
                        peers = group.items("peers").map { it.toTailscalePeer() },
                    )
                },
            keyAuth = endpoint.flag("keyAuth"),
            canShareFiles = endpoint.flag("canShareFiles"),
            waitingFileCount = endpoint.number("waitingFileCount").toInt(),
            receivingFileCount = endpoint.number("receivingFileCount").toInt(),
            unreadFileCount = endpoint.number("unreadFileCount").toInt(),
        )
    }

private fun JsonObject.toTailscalePeer(): TailscalePeerState {
    val dns = text("dnsName").removeSuffix(".")
    return TailscalePeerState(
        id = dns.ifEmpty { text("hostName") },
        stableId = text("stableID"),
        hostName = text("hostName"),
        dnsName = dns,
        operatingSystem = text("os"),
        tailscaleIps = strings("tailscaleIPs"),
        sshHostKeys = strings("sshHostKeys"),
        online = flag("online"),
        exitNode = flag("exitNode"),
        exitNodeOption = flag("exitNodeOption"),
        shareeNode = flag("shareeNode"),
        expired = flag("expired"),
        active = flag("active"),
        receivedBytes = number("rxBytes"),
        transmittedBytes = number("txBytes"),
        keyExpiry = number("keyExpiry"),
        lastSeen = number("lastSeen"),
        canReceiveFiles = flag("canReceiveFiles"),
    )
}
