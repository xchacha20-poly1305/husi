package fr.husi.vpn

import fr.husi.proto.daemon.OpenConnectAuthChallenge
import fr.husi.proto.daemon.OpenConnectAuthForm
import fr.husi.proto.daemon.OpenConnectBrowserRequest
import fr.husi.proto.daemon.OpenConnectEndpointStatus
import fr.husi.proto.daemon.OpenConnectTunnelInfo

const val OPENCONNECT_STATE_CONNECTING = "connecting"
const val OPENCONNECT_STATE_AUTH_PENDING = "auth-pending"
const val OPENCONNECT_STATE_CONNECTED = "connected"
const val OPENCONNECT_STATE_ERROR = "error"

const val OPENCONNECT_FIELD_TEXT = "text"
const val OPENCONNECT_FIELD_PASSWORD = "password"
const val OPENCONNECT_FIELD_SELECT = "select"

data class OpenConnectAuthChoice(
    val value: String,
    val label: String,
)

data class OpenConnectAuthField(
    val submissionKey: String,
    val name: String,
    val label: String,
    val kind: String,
    val value: String,
    val options: List<OpenConnectAuthChoice>,
)

data class OpenConnectAuthFormState(
    val fields: List<OpenConnectAuthField>,
)

data class OpenConnectBrowserRequestState(
    val url: String,
    val finalUrl: String,
    val cacheId: String,
    val cookieNames: List<String>,
    val earlyCookieNames: List<String>,
    val headerNames: List<String>,
    val callbackUrlPrefixes: List<String>,
) {
    /**
     * Mirrors sing-openconnect's `validateBrowserRequest`: a request that the core would
     * reject is [OpenConnectBrowserCompletionMode.Invalid] here as well, so neither front end
     * can start an authentication flow the core will refuse.
     */
    val completionMode: OpenConnectBrowserCompletionMode
        get() {
            if (url.isEmpty()) return OpenConnectBrowserCompletionMode.Invalid
            val callbackMode = callbackUrlPrefixes.isNotEmpty()
            val cookieMode =
                finalUrl.isNotEmpty() || cookieNames.isNotEmpty() || earlyCookieNames.isNotEmpty()
            val headerMode = headerNames.isNotEmpty()
            if (listOf(callbackMode, cookieMode, headerMode).count { it } != 1) {
                return OpenConnectBrowserCompletionMode.Invalid
            }
            return when {
                callbackMode -> when {
                    callbackUrlPrefixes.hasEmptyOrDuplicate() -> OpenConnectBrowserCompletionMode.Invalid
                    else -> OpenConnectBrowserCompletionMode.Callback
                }

                cookieMode -> when {
                    finalUrl.isEmpty() || cookieNames.isEmpty() -> OpenConnectBrowserCompletionMode.Invalid
                    cookieNames.hasEmptyOrDuplicate() -> OpenConnectBrowserCompletionMode.Invalid
                    earlyCookieNames.hasEmptyOrDuplicate() -> OpenConnectBrowserCompletionMode.Invalid
                    earlyCookieNames.any { it in cookieNames } -> OpenConnectBrowserCompletionMode.Invalid
                    else -> OpenConnectBrowserCompletionMode.Cookie
                }

                else -> when {
                    headerNames.hasEmptyOrDuplicate(ignoreCase = true) -> OpenConnectBrowserCompletionMode.Invalid
                    else -> OpenConnectBrowserCompletionMode.Header
                }
            }
        }
}

enum class OpenConnectBrowserCompletionMode {
    Callback,
    Cookie,
    Header,
    Invalid,
}

private fun List<String>.hasEmptyOrDuplicate(ignoreCase: Boolean = false): Boolean {
    val seen = mutableSetOf<String>()
    for (value in this) {
        if (value.isEmpty()) return true
        if (!seen.add(if (ignoreCase) value.lowercase() else value)) return true
    }
    return false
}

data class OpenConnectBrowserResultState(
    val finalUrl: String,
    val cookies: Map<String, String>,
    val headers: Map<String, String>,
)

fun OpenConnectBrowserRequestState.buildResult(
    finalUrl: String,
    cookies: Map<String, String>,
    headers: Map<String, String>,
): OpenConnectBrowserResultState {
    if (completionMode == OpenConnectBrowserCompletionMode.Cookie) {
        for (name in earlyCookieNames) {
            val value = cookies[name].orEmpty()
            if (value.isNotEmpty()) {
                return OpenConnectBrowserResultState("", mapOf(name to value), emptyMap())
            }
        }
    }
    return when (completionMode) {
        OpenConnectBrowserCompletionMode.Callback -> OpenConnectBrowserResultState(finalUrl, emptyMap(), emptyMap())
        OpenConnectBrowserCompletionMode.Cookie -> OpenConnectBrowserResultState(
            finalUrl,
            cookies.filterKeys { it in cookieNames },
            emptyMap(),
        )

        OpenConnectBrowserCompletionMode.Header -> OpenConnectBrowserResultState("", emptyMap(), headers)
        OpenConnectBrowserCompletionMode.Invalid -> OpenConnectBrowserResultState(finalUrl, cookies, headers)
    }
}

data class OpenConnectAuthChallengeState(
    val id: String,
    val banner: String,
    val message: String,
    val error: String,
    val form: OpenConnectAuthFormState?,
    val browser: OpenConnectBrowserRequestState?,
)

data class OpenConnectTunnelInfoState(
    val server: String,
    val flavor: String,
    val transport: String,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val dns: List<String>,
    val mtu: Int,
    val connectedSince: Long,
)

data class OpenConnectEndpointState(
    val tag: String,
    val state: String,
    val error: String,
    val authChallenge: OpenConnectAuthChallengeState?,
    val tunnelInfo: OpenConnectTunnelInfoState?,
)

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
