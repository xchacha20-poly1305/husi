package fr.husi.bg

import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.auth_required
import fr.husi.vpn.VpnAuthPendingNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.context.GlobalContext

internal class DesktopVpnAuthWatcher(
    private val title: StringResource,
    private val logLabel: String,
    private val pending: CoreClient.() -> Flow<VpnAuthPendingNotice?>,
) {
    private var scope: CoroutineScope? = null
    private var pendingAuth: VpnAuthPendingNotice? = null

    fun start() {
        if (scope != null) return
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = watchScope
        val coreClient: CoreClient = GlobalContext.get().get()
        watchScope.launch {
            try {
                coreClient.pending().collect { pending ->
                    if (pending == pendingAuth) return@collect
                    pendingAuth = pending
                    if (pending != null) {
                        val repository = resolveRepository()
                        DesktopNotificationCenter.show(
                            title = repository.getString(title),
                            message = "${repository.getString(Res.string.auth_required)}: ${pending.endpointTag}",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w(logLabel, e)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        pendingAuth = null
    }
}
