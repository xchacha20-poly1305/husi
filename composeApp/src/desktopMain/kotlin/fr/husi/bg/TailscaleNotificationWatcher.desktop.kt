package fr.husi.bg

import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.taildrop_received_notification
import fr.husi.resources.tailscale_state_needs_login
import fr.husi.resources.tailscale_with_tag
import fr.husi.tailscale.TailscaleNotice
import fr.husi.tailscale.TailscaleNoticeSnapshot
import fr.husi.tailscale.tailscaleNotices
import fr.husi.tailscale.toNoticeSnapshots
import fr.husi.tailscale.toEndpointStates
import fr.husi.ui.NavRoutes
import fr.husi.ui.PendingNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Desktop half of the Tailscale notification watcher.
 * Compares each status update with [tailscaleNotices] and posts through [DesktopNotificationCenter].
 * Activating a notification opens the window and requests the inbox or endpoint route.
 */
internal object TailscaleNotificationWatcher {
    private var scope: CoroutineScope? = null

    fun start() {
        if (scope != null) return
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = watchScope
        val coreClient: CoreClient = GlobalContext.get().get()
        watchScope.launch {
            try {
                var previous = emptyMap<String, TailscaleNoticeSnapshot>()
                coreClient.subscribeTailscaleStatus().collect { update ->
                    val endpoints = update.toEndpointStates()
                    val notices = tailscaleNotices(previous, endpoints)
                    previous = endpoints.toNoticeSnapshots()
                    for (notice in notices) {
                        show(notice)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("tailscale notification watcher", e)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    private suspend fun show(notice: TailscaleNotice) {
        val repository = resolveRepository()
        val title = repository.getString(Res.string.tailscale_with_tag, notice.endpointTag)
        when (notice) {
            is TailscaleNotice.FilesReceived -> DesktopNotificationCenter.show(
                title = title,
                message = repository.getPluralString(
                    Res.plurals.taildrop_received_notification,
                    notice.count,
                    notice.count,
                ),
                onActivated = {
                    PendingNavigation.request(NavRoutes.Tailscale.TaildropInbox(notice.endpointTag))
                },
            )

            is TailscaleNotice.NeedsLogin -> DesktopNotificationCenter.show(
                title = title,
                message = repository.getString(Res.string.tailscale_state_needs_login),
                onActivated = {
                    PendingNavigation.request(NavRoutes.Tailscale.Endpoint(notice.endpointTag))
                },
            )
        }
    }
}
