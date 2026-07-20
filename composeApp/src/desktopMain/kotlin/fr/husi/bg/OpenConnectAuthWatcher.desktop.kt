package fr.husi.bg

import fr.husi.libcore.Libcore
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.openconnect_authentication
import fr.husi.resources.auth_required
import fr.husi.utils.LibcoreClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal object OpenConnectAuthWatcher {

    private data class PendingAuth(val endpointTag: String, val challengeId: String)

    private var scope: CoroutineScope? = null
    private var pendingAuth: PendingAuth? = null

    fun start() {
        if (scope != null) return
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = watchScope
        LibcoreClientManager().subscribeOpenConnectStatus(watchScope) { iterator ->
            var pending: PendingAuth? = null
            while (iterator.hasNext()) {
                val status = iterator.next() ?: continue
                val challenge = status.authChallenge
                if (status.state == Libcore.OpenConnectStateAuthPending && challenge != null) {
                    pending = PendingAuth(status.tag, challenge.id)
                    break
                }
            }
            if (pending == pendingAuth) return@subscribeOpenConnectStatus
            pendingAuth = pending
            if (pending != null) {
                val repository = resolveRepository()
                watchScope.launch {
                    DesktopNotificationCenter.show(
                        title = repository.getString(Res.string.openconnect_authentication),
                        message = "${repository.getString(Res.string.auth_required)}: ${pending.endpointTag}",
                    )
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        pendingAuth = null
    }
}
