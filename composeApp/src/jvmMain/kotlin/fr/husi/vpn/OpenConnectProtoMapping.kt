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
    authChallenge = if (hasAuthChallenge()) authChallenge.toState() else null,
    tunnelInfo = if (hasTunnelInfo()) tunnelInfo.toState() else null,
)

fun OpenConnectAuthChallenge.toState() = OpenConnectAuthChallengeState(
    id = id,
    banner = banner,
    message = message,
    error = error,
    form = if (hasForm()) form.toState() else null,
    browser = if (hasBrowser()) browser.toState() else null,
)

fun OpenConnectAuthForm.toState(): OpenConnectAuthFormState {
    val fields = fieldsList.map { field ->
        OpenConnectAuthField(
            submissionKey = field.submissionKey,
            name = field.name,
            label = field.label,
            kind = field.kind,
            value = field.value,
            options = field.optionsList.map { option ->
                OpenConnectAuthChoice(value = option.value, label = option.label)
            },
        )
    }
    return OpenConnectAuthFormState(fields = fields)
}

fun OpenConnectBrowserRequest.toState() = OpenConnectBrowserRequestState(
    url = url,
    finalUrl = finalURL,
    cacheId = cacheID,
    cookieNames = cookieNamesList,
    earlyCookieNames = earlyCookieNamesList,
    headerNames = headerNamesList,
    callbackUrlPrefixes = callbackURLPrefixesList,
)

fun OpenConnectTunnelInfo.toState() = OpenConnectTunnelInfoState(
    server = server,
    flavor = flavor,
    transport = transport,
    ipv4 = ipv4List,
    ipv6 = ipv6List,
    dns = dnsList,
    mtu = mtu,
    connectedSince = connectedSince,
)
