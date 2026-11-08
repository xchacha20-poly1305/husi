package fr.husi.ui.openconnect

import fr.husi.remote.RemoteState
import fr.husi.remote.items
import fr.husi.remote.number
import fr.husi.remote.obj
import fr.husi.remote.request
import fr.husi.remote.strings
import fr.husi.remote.text
import fr.husi.vpn.OpenConnectAuthChallengeState
import fr.husi.vpn.OpenConnectAuthChoice
import fr.husi.vpn.OpenConnectAuthField
import fr.husi.vpn.OpenConnectAuthFormState
import fr.husi.vpn.OpenConnectBrowserRequestState
import fr.husi.vpn.OpenConnectBrowserResultState
import fr.husi.vpn.OpenConnectEndpointState
import fr.husi.vpn.OpenConnectTunnelInfoState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class RemoteOpenConnectActions(private val state: RemoteState) : OpenConnectAuthActions {
    override val endpoints: StateFlow<List<OpenConnectEndpointState>>
        field = MutableStateFlow(emptyList())

    fun update(value: JsonObject) {
        endpoints.value =
            value.items("endpoints").map { endpoint ->
                OpenConnectEndpointState(
                    tag = endpoint.text("endpointTag"),
                    state = endpoint.text("state"),
                    error = endpoint.text("error"),
                    authChallenge =
                        endpoint
                            .obj("authChallenge")
                            .takeIf { it.isNotEmpty() }
                            ?.let(::openConnectChallenge),
                    tunnelInfo =
                        endpoint
                            .obj("tunnelInfo")
                            .takeIf { it.isNotEmpty() }
                            ?.let { tunnel ->
                                OpenConnectTunnelInfoState(
                                    server = tunnel.text("server"),
                                    flavor = tunnel.text("flavor"),
                                    transport = tunnel.text("transport"),
                                    ipv4 = tunnel.strings("ipv4"),
                                    ipv6 = tunnel.strings("ipv6"),
                                    dns = tunnel.strings("dns"),
                                    mtu = tunnel.number("mtu").toInt(),
                                    connectedSince = tunnel.number("connectedSince"),
                                )
                            },
                )
            }
    }

    override suspend fun submitAuthChallenge(
        endpointTag: String,
        challenge: OpenConnectAuthChallengeState,
        formValues: Map<String, String>?,
        browserResult: OpenConnectBrowserResultState?,
    ): String? =
        state.perform(
            "SubmitOpenConnectAuthResponse",
            buildJsonObject {
                put("endpointTag", endpointTag)
                put("challengeID", challenge.id)
                if (formValues != null)
                    put(
                        "form",
                        buildJsonObject {
                            put(
                                "values",
                                JsonObject(formValues.mapValues { JsonPrimitive(it.value) }),
                            )
                        },
                    )
                else if (browserResult != null)
                    put(
                        "browser",
                        buildJsonObject {
                            put("finalURL", browserResult.finalUrl)
                            put(
                                "cookies",
                                JsonArray(
                                    browserResult.cookies.map { (key, value) ->
                                        request("name" to key, "value" to value)
                                    },
                                ),
                            )
                            put(
                                "headers",
                                JsonArray(
                                    browserResult.headers.map { (key, value) ->
                                        buildJsonObject {
                                            put("name", key)
                                            put(
                                                "values",
                                                JsonArray(
                                                    value
                                                        .lineSequence()
                                                        .filter { it.isNotBlank() }
                                                        .map(::JsonPrimitive)
                                                        .toList(),
                                                ),
                                            )
                                        }
                                    },
                                ),
                            )
                        },
                    )
            },
        )

    override suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String) =
        state.perform(
            "CancelOpenConnectAuthChallenge",
            request("endpointTag" to endpointTag, "challengeID" to challengeId),
        )
}

private fun openConnectChallenge(challenge: JsonObject) =
    OpenConnectAuthChallengeState(
        id = challenge.text("id"),
        banner = challenge.text("banner"),
        message = challenge.text("message"),
        error = challenge.text("error"),
        form =
            challenge
                .obj("form")
                .takeIf { it.isNotEmpty() }
                ?.let { form ->
                    OpenConnectAuthFormState(
                        fields =
                            form.items("fields").map { field ->
                                OpenConnectAuthField(
                                    submissionKey = field.text("submissionKey"),
                                    name = field.text("name"),
                                    label = field.text("label"),
                                    kind = field.text("kind"),
                                    value = field.text("value"),
                                    options =
                                        field.items("options").map { option ->
                                            OpenConnectAuthChoice(
                                                value = option.text("value"),
                                                label = option.text("label"),
                                            )
                                        },
                                )
                            },
                    )
                },
        browser =
            challenge
                .obj("browser")
                .takeIf { it.isNotEmpty() }
                ?.let { browser ->
                    OpenConnectBrowserRequestState(
                        url = browser.text("url"),
                        finalUrl = browser.text("finalURL"),
                        cacheId = browser.text("cacheID"),
                        cookieNames = browser.strings("cookieNames"),
                        earlyCookieNames = browser.strings("earlyCookieNames"),
                        headerNames = browser.strings("headerNames"),
                        callbackUrlPrefixes = browser.strings("callbackURLPrefixes"),
                    )
                },
    )
