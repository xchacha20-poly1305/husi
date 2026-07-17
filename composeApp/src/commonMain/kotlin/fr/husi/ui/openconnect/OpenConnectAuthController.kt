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
import fr.husi.libcore.OpenConnectAuthForm as LibcoreAuthForm
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
    val id: String,
    val banner: String,
    val message: String,
    val error: String,
    val url: String,
    val fields: List<OpenConnectAuthField>,
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
    val authForm: OpenConnectAuthFormState?,
    val tunnelInfo: OpenConnectTunnelInfoState?,
)

data class PendingOpenConnectAuth(
    val endpointTag: String,
    val form: OpenConnectAuthFormState,
)

/**
 * Long-lived owner of the OpenConnect endpoint status subscription.
 *
 * The auth form lives in the core as part of the endpoint state; this
 * controller only mirrors it. Dismissing the dialog hides it locally
 * (the form stays pending in the core and remains reachable from the
 * status page) while [cancelAuthForm] actually aborts authentication.
 */
class OpenConnectAuthController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clientManager = LibcoreClientManager()
    private var subscriptionJob: Job? = null

    val endpoints: StateFlow<List<OpenConnectEndpointState>>
        field = MutableStateFlow(emptyList())

    private val dismissedForms = MutableStateFlow<Set<String>>(emptySet())

    val pendingDialogAuth: StateFlow<PendingOpenConnectAuth?> =
        combine(endpoints, dismissedForms) { endpointList, dismissed ->
            endpointList.firstNotNullOfOrNull { endpoint ->
                val form = endpoint.authForm?.takeIf {
                    endpoint.state == OPENCONNECT_STATE_AUTH_PENDING &&
                        formKey(endpoint.tag, it.id) !in dismissed
                } ?: return@firstNotNullOfOrNull null
                PendingOpenConnectAuth(endpoint.tag, form)
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
        dismissedForms.value = emptySet()
        clientManager.close()
    }

    /** Hide the dialog for this form without cancelling authentication. */
    fun dismissDialog(endpointTag: String, formId: String) {
        dismissedForms.update { it + formKey(endpointTag, formId) }
    }

    /** @return an error message, or null on success. */
    suspend fun submitAuthForm(
        endpointTag: String,
        form: OpenConnectAuthFormState,
        values: Map<String, String>,
    ): String? = withContext(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                val formValues = Libcore.newOpenConnectFormValues()
                for ((key, value) in values) {
                    formValues.add(key, value)
                }
                client.completeOpenConnectAuthForm(endpointTag, form.id, formValues)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("submit openconnect auth form", e)
            return@withContext e.message ?: "submit auth form failed"
        }
        persistFormEntries(endpointTag, form, values)
        null
    }

    /**
     * Remember the submitted non-secret answers in the profile's
     * [OpenConnectBean.formEntries] so the next connection can replay
     * them without interaction.
     */
    private suspend fun persistFormEntries(
        endpointTag: String,
        form: OpenConnectAuthFormState,
        values: Map<String, String>,
    ) {
        val newEntries = form.fields.mapNotNull { field ->
            if (field.kind == "password") return@mapNotNull null
            val value = values[field.submissionKey] ?: return@mapNotNull null
            if (value.isBlank()) return@mapNotNull null
            OpenConnectFormEntry(
                formId = form.id,
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
    suspend fun cancelAuthForm(endpointTag: String, formId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                clientManager.withClient { client ->
                    client.cancelOpenConnectAuthForm(endpointTag, formId)
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("cancel openconnect auth form", e)
                e.message ?: "cancel auth form failed"
            }
        }

    private fun formKey(endpointTag: String, formId: String): String = "$endpointTag\n$formId"
}

private fun LibcoreEndpointStatus.toState() = OpenConnectEndpointState(
    tag = tag,
    state = state,
    error = error,
    authForm = authForm?.toState(),
    tunnelInfo = tunnelInfo?.toState(),
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
        id = id,
        banner = banner,
        message = message,
        error = error,
        url = url,
        fields = fields,
    )
}

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
