package fr.husi.ui.openconnect

import fr.husi.bg.BackendState
import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openconnect.OpenConnectFormEntry
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.proto.daemon.openConnectAuthFormResponse
import fr.husi.proto.daemon.openConnectAuthResponseSubmission
import fr.husi.proto.daemon.openConnectBrowserCookie
import fr.husi.proto.daemon.openConnectBrowserHeader
import fr.husi.proto.daemon.openConnectBrowserResult
import fr.husi.vpn.OPENCONNECT_FIELD_PASSWORD
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.vpn.OpenConnectAuthChallengeState
import fr.husi.vpn.OpenConnectBrowserResultState
import fr.husi.vpn.OpenConnectEndpointState
import fr.husi.vpn.toState
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
import org.koin.core.context.GlobalContext

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
class OpenConnectAuthController(
    private val coreClient: CoreClient = GlobalContext.get().get(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        subscriptionJob = scope.launch {
            try {
                coreClient.subscribeOpenConnectStatus().collect { update ->
                    endpoints.value = update.endpointsList.map { it.toState() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("subscribe openconnect status", e)
            }
        }
    }

    private fun stop() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        endpoints.value = emptyList()
        dismissedChallenges.value = emptySet()
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
            val submission = openConnectAuthResponseSubmission {
                this.endpointTag = endpointTag
                challengeID = challenge.id
                if (formValues != null) {
                    form = openConnectAuthFormResponse {
                        values.putAll(formValues)
                    }
                } else if (browserResult != null) {
                    browser = openConnectBrowserResult {
                        finalURL = browserResult.finalUrl
                        for ((name, value) in browserResult.cookies) {
                            if (value.isNotEmpty()) {
                                cookies += openConnectBrowserCookie {
                                    this.name = name
                                    this.value = value
                                }
                            }
                        }
                        for ((name, value) in browserResult.headers) {
                            val headerValues = value.lineSequence()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toList()
                            if (headerValues.isNotEmpty()) {
                                headers += openConnectBrowserHeader {
                                    this.name = name
                                    values.addAll(headerValues)
                                }
                            }
                        }
                    }
                }
            }
            coreClient.submitOpenConnectAuthResponse(submission)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("submit openconnect auth challenge", e)
            return@withContext e.readableMessage
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
            if (field.kind == OPENCONNECT_FIELD_PASSWORD) return@mapNotNull null
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
                coreClient.cancelOpenConnectAuthChallenge(endpointTag, challengeId)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("cancel openconnect auth challenge", e)
                e.readableMessage
            }
        }

    private fun challengeKey(endpointTag: String, challengeId: String): String = "$endpointTag\n$challengeId"
}
