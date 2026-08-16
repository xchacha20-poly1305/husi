package fr.husi.bg

import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.openconnect_authentication
import fr.husi.resources.auth_required
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

internal object OpenConnectAuthWatcher {

    private data class PendingAuth(val endpointTag: String, val challengeId: String)

    private var scope: CoroutineScope? = null
    private var pendingAuth: PendingAuth? = null

    fun start() {
        if (scope != null) return
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = watchScope
        val coreClient: CoreClient = GlobalContext.get().get()
        watchScope.launch {
            try {
                coreClient.subscribeOpenConnectStatus().collect { update ->
                    var pending: PendingAuth? = null
                    for (status in update.endpointsList) {
                        if (status.state == OPENCONNECT_STATE_AUTH_PENDING && status.hasAuthChallenge()) {
                            pending = PendingAuth(status.endpointTag, status.authChallenge.id)
                            break
                        }
                    }
                    if (pending == pendingAuth) return@collect
                    pendingAuth = pending
                    if (pending != null) {
                        val repository = resolveRepository()
                        DesktopNotificationCenter.show(
                            title = repository.getString(Res.string.openconnect_authentication),
                            message = "${repository.getString(Res.string.auth_required)}: ${pending.endpointTag}",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("openconnect auth watcher", e)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        pendingAuth = null
    }
}
