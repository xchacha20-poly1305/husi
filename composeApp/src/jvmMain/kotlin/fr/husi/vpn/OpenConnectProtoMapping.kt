package fr.husi.vpn

import fr.husi.proto.daemon.OpenConnectAuthChallenge
import fr.husi.proto.daemon.OpenConnectAuthForm
import fr.husi.proto.daemon.OpenConnectBrowserRequest
import fr.husi.proto.daemon.OpenConnectEndpointStatus
import fr.husi.proto.daemon.OpenConnectTunnelInfo

fun OpenConnectEndpointStatus.toState() = OpenConnectEndpointState(
    tag = endpointTag,
    state = state,
    error = error,
    authChallenge = authChallenge?.toState(),
    tunnelInfo = tunnelInfo?.toState(),
)

fun OpenConnectAuthChallenge.toState() = OpenConnectAuthChallengeState(
    id = id,
    banner = banner,
    message = message,
    error = error,
    form = form?.toState(),
    browser = browser?.toState(),
)

fun OpenConnectAuthForm.toState(): OpenConnectAuthFormState {
    val fields = fields.map { field ->
        OpenConnectAuthField(
            submissionKey = field.submissionKey,
            name = field.name,
            label = field.label,
            kind = field.kind,
            value = field.value_,
            options = field.options.map { option ->
                OpenConnectAuthChoice(value = option.value_, label = option.label)
            },
        )
    }
    return OpenConnectAuthFormState(fields = fields)
}

fun OpenConnectBrowserRequest.toState() = OpenConnectBrowserRequestState(
    url = url,
    finalUrl = finalURL,
    cacheId = cacheID,
    cookieNames = cookieNames,
    earlyCookieNames = earlyCookieNames,
    headerNames = headerNames,
    callbackUrlPrefixes = callbackURLPrefixes,
)

fun OpenConnectTunnelInfo.toState() = OpenConnectTunnelInfoState(
    server = server,
    flavor = flavor,
    transport = transport,
    ipv4 = ipv4,
    ipv6 = ipv6,
    dns = dns,
    mtu = mtu,
    connectedSince = connectedSince,
)
