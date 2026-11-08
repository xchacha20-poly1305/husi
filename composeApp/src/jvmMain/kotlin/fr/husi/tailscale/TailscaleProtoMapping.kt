package fr.husi.tailscale

import fr.husi.proto.daemon.TailscaleEndpointStatus
import fr.husi.proto.daemon.TailscalePeer
import fr.husi.proto.daemon.TailscaleStatusUpdate
import fr.husi.proto.daemon.TailscaleUserGroup

fun TailscaleStatusUpdate.toEndpointStates(): List<TailscaleEndpointState> =
    endpointsList.map { it.toState() }

fun TailscaleEndpointStatus.toState(): TailscaleEndpointState = TailscaleEndpointState(
    endpointTag = endpointTag,
    backendState = backendState,
    stateText = stateText,
    authUrl = authURL,
    networkName = networkName,
    magicDnsSuffix = magicDNSSuffix,
    selfPeer = if (hasSelf()) {
        self.toState()
    } else {
        null
    },
    exitNode = if (hasExitNode()) {
        exitNode.toState()
    } else {
        null
    },
    userGroups = userGroupsList.map { it.toState() },
    keyAuth = keyAuth,
    canShareFiles = canShareFiles,
    waitingFileCount = waitingFileCount,
    receivingFileCount = receivingFileCount,
    unreadFileCount = unreadFileCount,
)

fun TailscaleUserGroup.toState(): TailscaleUserGroupState = TailscaleUserGroupState(
    userId = userID,
    loginName = loginName,
    displayName = displayName,
    profilePictureUrl = profilePicURL,
    peers = peersList.map { it.toState() },
)

fun TailscalePeer.toState(): TailscalePeerState {
    val magicDNS = dnsName.removeSuffix(".")
    return TailscalePeerState(
        id = magicDNS.ifEmpty { hostName },
        stableId = stableID,
        hostName = hostName,
        dnsName = magicDNS,
        operatingSystem = os,
        tailscaleIps = tailscaleIPsList,
        sshHostKeys = sshHostKeysList,
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
