package fr.husi.vpn

import fr.husi.proto.daemon.OpenVPNChallenge
import fr.husi.proto.daemon.OpenVPNEndpointStatus
import fr.husi.proto.daemon.OpenVPNTunnelInfo

fun OpenVPNEndpointStatus.toState() = OpenVPNEndpointState(
    tag = endpointTag,
    state = state,
    stateText = stateText,
    error = error,
    challenge = challenge?.toState(),
    tunnelInfo = tunnelInfo?.toState(),
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
    ipv4 = ipv4,
    ipv6 = ipv6,
    dns = dns,
    mtu = mtu,
    connectedSince = connectedSince,
)
