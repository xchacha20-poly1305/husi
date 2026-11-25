package fr.husi.ui.openconnect

import fr.husi.proto.daemon.OpenConnectAuthChallenge
import fr.husi.proto.daemon.OpenConnectAuthChallengeCancel
import fr.husi.proto.daemon.OpenConnectAuthFormResponse
import fr.husi.proto.daemon.OpenConnectAuthResponseSubmission
import fr.husi.proto.daemon.OpenConnectBrowserCookie
import fr.husi.proto.daemon.OpenConnectBrowserHeader
import fr.husi.proto.daemon.OpenConnectBrowserResult
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.remote.RemoteState
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.vpn.OpenConnectAuthChallengeState
import fr.husi.vpn.OpenConnectAuthChoice
import fr.husi.vpn.OpenConnectAuthField
import fr.husi.vpn.OpenConnectAuthFormState
import fr.husi.vpn.OpenConnectBrowserRequestState
import fr.husi.vpn.OpenConnectBrowserResultState
import fr.husi.vpn.OpenConnectEndpointState
import fr.husi.vpn.OpenConnectTunnelInfoState
import fr.husi.vpn.PendingVpnAuth
import fr.husi.vpn.vpnAuthChallengeKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class RemoteOpenConnectActions(private val state: RemoteState) : OpenConnectAuthActions {
    override val endpoints: StateFlow<List<OpenConnectEndpointState>>
        field = MutableStateFlow(emptyList())

    val pendingChallengeKeys: StateFlow<Set<String>>
        field = MutableStateFlow(emptySet())

    val pendingDialogAuth: StateFlow<PendingOpenConnectAuth?>
        field = MutableStateFlow(null)

    private val dismissedChallenges = mutableSetOf<String>()

    fun update(value: OpenConnectStatusUpdate): List<OpenConnectEndpointState> {
        val mapped = value.endpoints.map { endpoint ->
            OpenConnectEndpointState(
                tag = endpoint.endpointTag,
                state = endpoint.state,
                error = endpoint.error,
                authChallenge = endpoint.authChallenge?.let(::openConnectChallenge),
                tunnelInfo = endpoint.tunnelInfo?.let { tunnel ->
                    OpenConnectTunnelInfoState(
                        server = tunnel.server,
                        flavor = tunnel.flavor,
                        transport = tunnel.transport,
                        ipv4 = tunnel.ipv4,
                        ipv6 = tunnel.ipv6,
                        dns = tunnel.dns,
                        mtu = tunnel.mtu,
                        connectedSince = tunnel.connectedSince,
                    )
                },
            )
        }
        endpoints.value = mapped
        recomputePending(mapped)
        return mapped
    }

    fun dismissDialog(endpointTag: String, challengeId: String) {
        dismissedChallenges.add(vpnAuthChallengeKey(endpointTag, challengeId))
        recomputePending(endpoints.value)
    }

    fun undismissDialog(endpointTag: String, challengeId: String) {
        if (dismissedChallenges.remove(vpnAuthChallengeKey(endpointTag, challengeId))) {
            recomputePending(endpoints.value)
        }
    }

    fun clearDismissed() {
        if (dismissedChallenges.isEmpty()) return
        dismissedChallenges.clear()
        recomputePending(endpoints.value)
    }

    private fun recomputePending(list: List<OpenConnectEndpointState>) {
        val keys = HashSet<String>()
        var firstDialog: PendingOpenConnectAuth? = null
        for (endpoint in list) {
            val challenge = endpoint.authChallenge?.takeIf {
                endpoint.state == OPENCONNECT_STATE_AUTH_PENDING
            } ?: continue
            val key = vpnAuthChallengeKey(endpoint.tag, challenge.id)
            keys.add(key)
            if (firstDialog == null && key !in dismissedChallenges) {
                firstDialog = PendingVpnAuth(endpoint.tag, challenge)
            }
        }
        pendingChallengeKeys.value = keys
        pendingDialogAuth.value = firstDialog
    }

    override suspend fun submitAuthChallenge(
        endpointTag: String,
        challenge: OpenConnectAuthChallengeState,
        formValues: Map<String, String>?,
        browserResult: OpenConnectBrowserResultState?,
    ): String? = state.perform {
        SubmitOpenConnectAuthResponse().execute(
            OpenConnectAuthResponseSubmission(
                endpointTag = endpointTag,
                challengeID = challenge.id,
                form = formValues?.let { OpenConnectAuthFormResponse(it) },
                browser = if (formValues == null) browserResult?.let { result ->
                    OpenConnectBrowserResult(
                        finalURL = result.finalUrl,
                        cookies = result.cookies.map { (name, value) -> OpenConnectBrowserCookie(name, value) },
                        headers = result.headers.map { (name, values) ->
                            OpenConnectBrowserHeader(name, values.lineSequence().filter { it.isNotBlank() }.toList())
                        },
                    )
                } else null,
            ),
        )
    }

    override suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String) = state.perform {
        CancelOpenConnectAuthChallenge().execute(OpenConnectAuthChallengeCancel(endpointTag, challengeId))
    }
}

private fun openConnectChallenge(challenge: OpenConnectAuthChallenge) =
    OpenConnectAuthChallengeState(
        id = challenge.id,
        banner = challenge.banner,
        message = challenge.message,
        error = challenge.error,
        form =
            challenge
                .form
                ?.let { form ->
                    OpenConnectAuthFormState(
                        fields =
                            form.fields.map { field ->
                                OpenConnectAuthField(
                                    submissionKey = field.submissionKey,
                                    name = field.name,
                                    label = field.label,
                                    kind = field.kind,
                                    value = field.value_,
                                    options =
                                        field.options.map { option ->
                                            OpenConnectAuthChoice(
                                                value = option.value_,
                                                label = option.label,
                                            )
                                        },
                                )
                            },
                    )
                },
        browser =
            challenge
                .browser
                ?.let { browser ->
                    OpenConnectBrowserRequestState(
                        url = browser.url,
                        finalUrl = browser.finalURL,
                        cacheId = browser.cacheID,
                        cookieNames = browser.cookieNames,
                        earlyCookieNames = browser.earlyCookieNames,
                        headerNames = browser.headerNames,
                        callbackUrlPrefixes = browser.callbackURLPrefixes,
                    )
                },
    )
