package fr.husi.ui.openconnect

import fr.husi.bg.BackendState
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openconnect.OpenConnectFormEntry
import fr.husi.ktx.Logs
import fr.husi.libcore.Libcore
import fr.husi.libcore.OpenConnectAuthChallenge as LibcoreAuthChallenge
import fr.husi.libcore.OpenConnectAuthForm as LibcoreAuthForm
import fr.husi.libcore.OpenConnectBrowserRequest as LibcoreBrowserRequest
import fr.husi.libcore.OpenConnectEndpointStatus as LibcoreEndpointStatus
import fr.husi.libcore.OpenConnectTunnelInfo as LibcoreTunnelInfo
import fr.husi.libcore.StringIterator
import fr.husi.utils.LibcoreClientManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val OPENCONNECT_STATE_CONNECTING = "connecting"
const val OPENCONNECT_STATE_AUTH_PENDING = "auth-pending"
const val OPENCONNECT_STATE_CONNECTED = "connected"
const val OPENCONNECT_STATE_ERROR = "error"

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
    val completionMode: OpenConnectBrowserCompletionMode
        get() = when {
            callbackUrlPrefixes.isNotEmpty() && finalUrl.isEmpty() && cookieNames.isEmpty() &&
                earlyCookieNames.isEmpty() && headerNames.isEmpty() -> OpenConnectBrowserCompletionMode.Callback
            callbackUrlPrefixes.isEmpty() && finalUrl.isNotEmpty() && cookieNames.isNotEmpty() &&
                headerNames.isEmpty() -> OpenConnectBrowserCompletionMode.Cookie
            callbackUrlPrefixes.isEmpty() && finalUrl.isEmpty() && cookieNames.isEmpty() &&
                earlyCookieNames.isEmpty() && headerNames.isNotEmpty() -> OpenConnectBrowserCompletionMode.Header
            else -> OpenConnectBrowserCompletionMode.Invalid
        }
}

enum class OpenConnectBrowserCompletionMode {
    Callback,
    Cookie,
    Header,
    Invalid,
}

data class OpenConnectBrowserResultState(
    val finalUrl: String,
    val cookies: Map<String, String>,
    val headers: Map<String, String>,
)

internal fun OpenConnectBrowserRequestState.buildResult(
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

data class PendingOpenConnectAuth(
    val endpointTag: String,
    val challenge: OpenConnectAuthChallengeState,
)

/**
 * Long-lived owner of the OpenConnect endpoint status subscription.
 *
 * The auth challenge lives in the core as part of the endpoint state; this
 * controller only mirrors it. Dismissing the dialog hides it locally
 * (the challenge stays pending in the core and remains reachable from the
 * status page) while [cancelAuthChallenge] actually aborts authentication.
 */
class OpenConnectAuthController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clientManager = LibcoreClientManager()
    private var subscriptionJob: Job? = null

    val endpoints: StateFlow<List<OpenConnectEndpointState>>
        field = MutableStateFlow(emptyList())

    private val dismissedChallenges = MutableStateFlow<Set<String>>(emptySet())

    val pendingDialogAuth: StateFlow<PendingOpenConnectAuth?> =
        combine(endpoints, dismissedChallenges) { endpointList, dismissed ->
            endpointList.firstNotNullOfOrNull { endpoint ->
                val challenge = endpoint.authChallenge?.takeIf {
                    endpoint.state == OPENCONNECT_STATE_AUTH_PENDING &&
                        challengeKey(endpoint.tag, it.id) !in dismissed
                } ?: return@firstNotNullOfOrNull null
                PendingOpenConnectAuth(endpoint.tag, challenge)
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            BackendState.status
                .map { it.state.started }
                .distinctUntilChanged()
                .collect { started ->
                    if (started) start() else stop()
                }
        }
    }

    private fun start() {
        if (subscriptionJob != null) return
        subscriptionJob = clientManager.subscribeOpenConnectStatus(scope) { iterator ->
            val statuses = buildList {
                while (iterator.hasNext()) {
                    add(iterator.next()?.toState() ?: continue)
                }
            }
            endpoints.value = statuses
        }
    }

    private suspend fun stop() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        endpoints.value = emptyList()
        dismissedChallenges.value = emptySet()
        clientManager.close()
    }

    /** Hide the dialog for this challenge without cancelling authentication. */
    fun dismissDialog(endpointTag: String, challengeId: String) {
        dismissedChallenges.update { it + challengeKey(endpointTag, challengeId) }
    }

    /** @return an error message, or null on success. */
    suspend fun submitAuthChallenge(
        endpointTag: String,
        challenge: OpenConnectAuthChallengeState,
        formValues: Map<String, String>?,
        browserResult: OpenConnectBrowserResultState?,
    ): String? = withContext(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                val libcoreFormValues = formValues?.let { values ->
                    Libcore.newOpenConnectFormValues().also { result ->
                        for ((key, value) in values) {
                            result.add(key, value)
                        }
                    }
                }
                val libcoreBrowserResult = browserResult?.let { result ->
                    Libcore.newOpenConnectBrowserResult(result.finalUrl).also { browser ->
                        for ((name, value) in result.cookies) {
                            if (value.isNotEmpty()) browser.addCookie(name, value)
                        }
                        for ((name, value) in result.headers) {
                            for (headerValue in value.lineSequence()) {
                                if (headerValue.isNotEmpty()) browser.addHeader(name, headerValue)
                            }
                        }
                    }
                }
                client.completeOpenConnectAuthChallenge(
                    endpointTag,
                    challenge.id,
                    Libcore.newOpenConnectAuthResponse(libcoreFormValues, libcoreBrowserResult),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("submit openconnect auth challenge", e)
            return@withContext e.message ?: "submit auth challenge failed"
        }
        if (challenge.form != null && formValues != null) {
            persistFormEntries(endpointTag, challenge, formValues)
        }
        null
    }

    /**
     * Remember the submitted non-secret answers in the profile's
     * [OpenConnectBean.formEntries] so the next connection can replay
     * them without interaction.
     */
    private suspend fun persistFormEntries(
        endpointTag: String,
        challenge: OpenConnectAuthChallengeState,
        values: Map<String, String>,
    ) {
        val newEntries = challenge.form?.fields.orEmpty().mapNotNull { field ->
            if (field.kind == "password") return@mapNotNull null
            val value = values[field.submissionKey] ?: return@mapNotNull null
            if (value.isBlank()) return@mapNotNull null
            OpenConnectFormEntry(
                formId = challenge.id,
                submissionKey = field.submissionKey,
                name = field.name,
                value = value,
            )
        }
        if (newEntries.isEmpty()) return
        try {
            val profile = findProfile(endpointTag) ?: return
            val bean = profile.requireBean() as OpenConnectBean
            val replacedKeys = newEntries.mapTo(HashSet()) { it.formId to it.submissionKey }
            bean.formEntries = (
                bean.formEntries.filterNot { (it.formId to it.submissionKey) in replacedKeys } +
                    newEntries
                ).toList()
            profile.putBean(bean)
            ProfileManager.updateProfile(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("persist openconnect form entries", e)
        }
    }

    private suspend fun findProfile(endpointTag: String): ProxyEntity? {
        SagerDatabase.proxyDao.getById(DataStore.currentProfile)?.let { current ->
            if (current.requireBean() is OpenConnectBean && current.displayName() == endpointTag) {
                return current
            }
        }
        val candidates = SagerDatabase.proxyDao.getAll().filter { entity ->
            entity.requireBean() is OpenConnectBean && entity.displayName() == endpointTag
        }
        return candidates.singleOrNull()
    }

    /** @return an error message, or null on success. */
    suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                clientManager.withClient { client ->
                    client.cancelOpenConnectAuthChallenge(endpointTag, challengeId)
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("cancel openconnect auth challenge", e)
                e.message ?: "cancel auth challenge failed"
            }
        }

    private fun challengeKey(endpointTag: String, challengeId: String): String = "$endpointTag\n$challengeId"
}

private fun LibcoreEndpointStatus.toState() = OpenConnectEndpointState(
    tag = tag,
    state = state,
    error = error,
    authChallenge = authChallenge?.toState(),
    tunnelInfo = tunnelInfo?.toState(),
)

private fun LibcoreAuthChallenge.toState() = OpenConnectAuthChallengeState(
    id = id,
    banner = banner,
    message = message,
    error = error,
    form = form?.toState(),
    browser = browser?.toState(),
)

private fun LibcoreAuthForm.toState(): OpenConnectAuthFormState {
    val fields = buildList {
        val iterator = fields
        while (iterator.hasNext()) {
            val field = iterator.next() ?: continue
            val options = buildList {
                val optionIterator = field.options
                while (optionIterator.hasNext()) {
                    val option = optionIterator.next() ?: continue
                    add(OpenConnectAuthChoice(value = option.value, label = option.label))
                }
            }
            add(
                OpenConnectAuthField(
                    submissionKey = field.submissionKey,
                    name = field.name,
                    label = field.label,
                    kind = field.kind,
                    value = field.value,
                    options = options,
                ),
            )
        }
    }
    return OpenConnectAuthFormState(
        fields = fields,
    )
}

private fun LibcoreBrowserRequest.toState() = OpenConnectBrowserRequestState(
    url = url,
    finalUrl = finalURL,
    cacheId = cacheID,
    cookieNames = cookieNames.toStringList(),
    earlyCookieNames = earlyCookieNames.toStringList(),
    headerNames = headerNames.toStringList(),
    callbackUrlPrefixes = callbackURLPrefixes.toStringList(),
)

private fun LibcoreTunnelInfo.toState() = OpenConnectTunnelInfoState(
    server = server,
    flavor = flavor,
    transport = transport,
    ipv4 = iPv4.toStringList(),
    ipv6 = iPv6.toStringList(),
    dns = dns.toStringList(),
    mtu = mtu,
    connectedSince = connectedSince,
)

private fun StringIterator.toStringList(): List<String> = buildList {
    while (hasNext()) {
        add(next())
    }
}
