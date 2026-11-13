package fr.husi.ui.openconnect

import fr.husi.core.remote.CoreTarget
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openconnect.OpenConnectFormEntry
import fr.husi.ktx.Logs
import fr.husi.proto.daemon.OpenConnectAuthFormResponse
import fr.husi.proto.daemon.OpenConnectAuthResponseSubmission
import fr.husi.proto.daemon.OpenConnectBrowserCookie
import fr.husi.proto.daemon.OpenConnectBrowserHeader
import fr.husi.proto.daemon.OpenConnectBrowserResult
import fr.husi.vpn.OPENCONNECT_FIELD_PASSWORD
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.vpn.OpenConnectAuthChallengeState
import fr.husi.vpn.OpenConnectBrowserResultState
import fr.husi.vpn.OpenConnectEndpointState
import fr.husi.vpn.PendingVpnAuth
import fr.husi.vpn.VpnAuthSession
import fr.husi.vpn.toState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

typealias PendingOpenConnectAuth = PendingVpnAuth<OpenConnectAuthChallengeState>

/**
 * Long-lived owner of the OpenConnect endpoint status subscription.
 *
 * The auth challenge lives in the core as part of the endpoint state; this
 * controller only mirrors it. Dismissing the dialog hides it locally
 * (the challenge stays pending in the core and remains reachable from the
 * status page) while [cancelAuthChallenge] actually aborts authentication.
 */
class OpenConnectAuthController(
    targets: StateFlow<CoreTarget?>,
) : OpenConnectAuthActions {
    private val session = VpnAuthSession(
        targets = targets,
        subscribe = { client ->
            client.subscribeOpenConnectStatus().map { update ->
                update.endpoints.map { it.toState() }
            }
        },
        pendingOf = { endpoint ->
            endpoint.authChallenge?.takeIf {
                endpoint.state == OPENCONNECT_STATE_AUTH_PENDING
            }?.let { PendingVpnAuth(endpoint.tag, it) }
        },
        challengeId = { it.id },
        logLabel = "openconnect",
    )

    override val endpoints: StateFlow<List<OpenConnectEndpointState>>
        get() = session.endpoints

    val pendingChallengeKeys: StateFlow<Set<String>>
        get() = session.pendingChallengeKeys

    val pendingDialogAuth: StateFlow<PendingOpenConnectAuth?>
        get() = session.pendingDialogAuth

    /** Hide the dialog for this challenge without cancelling authentication. */
    fun dismissDialog(endpointTag: String, challengeId: String) {
        session.dismissDialog(endpointTag, challengeId)
    }

    /** @return an error message, or null on success. */
    override suspend fun submitAuthChallenge(
        endpointTag: String,
        challenge: OpenConnectAuthChallengeState,
        formValues: Map<String, String>?,
        browserResult: OpenConnectBrowserResultState?,
    ): String? {
        val error = session.perform("submit openconnect auth challenge") { target ->
            val submission = OpenConnectAuthResponseSubmission(
                endpointTag = endpointTag,
                challengeID = challenge.id,
                form = formValues?.let { OpenConnectAuthFormResponse(values = it) },
                browser = if (formValues == null && browserResult != null)
                    OpenConnectBrowserResult(
                        finalURL = browserResult.finalUrl,
                        cookies = browserResult.cookies
                            .filterValues { it.isNotEmpty() }
                            .map { (name, value) ->
                                OpenConnectBrowserCookie(
                                    name = name,
                                    value_ = value,
                                )
                            },
                        headers = browserResult.headers.mapNotNull { (name, value) ->
                            value.lineSequence()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toList()
                                .takeIf { it.isNotEmpty() }
                                ?.let {
                                    OpenConnectBrowserHeader(
                                        name = name,
                                        values = it,
                                    )
                                }
                        },
                    )
                else null,
            )
            target.client.submitOpenConnectAuthResponse(submission)
            if (!target.isRemote && challenge.form != null && formValues != null) {
                persistFormEntries(endpointTag, challenge, formValues)
            }
        }
        return error
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
            bean.formEntries = (bean.formEntries.filterNot {
                (it.formId to it.submissionKey) in replacedKeys
            } + newEntries).toList()
            profile.putBean(bean)
            ProfileManager.updateProfile(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("persist openconnect form entries", e)
        }
    }

    private suspend fun findProfile(endpointTag: String): ProxyEntity? {
        SagerDatabase.proxyDao.getById(DataStore.currentProfile.get())?.let { current ->
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
    override suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String): String? =
        session.perform("cancel openconnect auth challenge") { target ->
            target.client.cancelOpenConnectAuthChallenge(endpointTag, challengeId)
        }
}
