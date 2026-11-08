package fr.husi.vpn

import fr.husi.proto.daemon.OpenVPNChallenge
import fr.husi.proto.daemon.OpenVPNEndpointStatus
import fr.husi.proto.daemon.OpenVPNTunnelInfo

fun OpenVPNEndpointStatus.toState() = OpenVPNEndpointState(
    tag = endpointTag,
    state = state,
    stateText = stateText,
    error = error,
    challenge = if (hasChallenge()) challenge.toState() else null,
    tunnelInfo = if (hasTunnelInfo()) tunnelInfo.toState() else null,
)

fun OpenVPNChallenge.toState() = OpenVPNChallengeState(
    id = id,
    kind = kind,
    username = username,
    message = message,
    url = url,
    secretMessage = secretMessage,
    echo = echo,
    previousError = previousError,
    deadline = deadline,
)

fun OpenVPNTunnelInfo.toState() = OpenVPNTunnelInfoState(
    server = server,
    network = network,
    cipher = cipher,
    ipv4 = ipv4List,
    ipv6 = ipv6List,
    dns = dnsList,
    mtu = mtu,
    connectedSince = connectedSince,
)
