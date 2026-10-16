package fr.husi.bg

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.ktx.hasPermission
import fr.husi.lib.R
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.taildrop_received_notification
import fr.husi.resources.tailscale_state_needs_login
import fr.husi.resources.tailscale_with_tag
import fr.husi.tailscale.TAILSCALE_STATE_NEEDS_LOGIN
import fr.husi.tailscale.TailscaleNotice
import fr.husi.tailscale.TailscaleNoticeSnapshot
import fr.husi.tailscale.tailscaleNotices
import fr.husi.tailscale.toEndpointStates
import fr.husi.tailscale.toNoticeSnapshots
import fr.husi.ui.TailscaleRouteIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

const val TAILSCALE_NOTIFICATION_CHANNEL_ID = "service-tailscale"

object TailscaleNotificationWatcher {
    private const val FILES_RECEIVED_NOTIFICATION_ID = 6
    private const val NEEDS_LOGIN_NOTIFICATION_ID = 7

    private var scope: CoroutineScope? = null
    private val postedTags = mutableSetOf<String>()

    fun start(context: Context) {
        if (scope != null) return
        val appContext = context.applicationContext
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = watchScope
        val coreClient: CoreClient = GlobalContext.get().get()
        watchScope.launch {
            try {
                var previous = emptyMap<String, TailscaleNoticeSnapshot>()
                coreClient.subscribeTailscaleStatus().collect { update ->
                    val endpoints = update.toEndpointStates()
                    for (notice in tailscaleNotices(previous, endpoints)) {
                        show(appContext, notice)
                    }
                    previous = endpoints.toNoticeSnapshots()
                    val manager = NotificationManagerCompat.from(appContext)
                    for (endpoint in endpoints) {
                        if (endpoint.backendState != TAILSCALE_STATE_NEEDS_LOGIN) {
                            manager.cancel(endpoint.endpointTag, NEEDS_LOGIN_NOTIFICATION_ID)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("tailscale notification watcher", e)
            }
        }
    }

    fun stop(context: Context) {
        scope?.cancel()
        scope = null
        val manager = NotificationManagerCompat.from(context.applicationContext)
        synchronized(postedTags) {
            for (tag in postedTags) {
                manager.cancel(tag, FILES_RECEIVED_NOTIFICATION_ID)
                manager.cancel(tag, NEEDS_LOGIN_NOTIFICATION_ID)
            }
            postedTags.clear()
        }
    }

    private suspend fun show(context: Context, notice: TailscaleNotice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return
        }
        val repository = resolveRepository()
        val (notificationId, text) = when (notice) {
            is TailscaleNotice.FilesReceived -> FILES_RECEIVED_NOTIFICATION_ID to repository.getPluralString(
                Res.plurals.taildrop_received_notification,
                notice.count,
                notice.count,
            )

            is TailscaleNotice.NeedsLogin -> NEEDS_LOGIN_NOTIFICATION_ID to
                repository.getString(Res.string.tailscale_state_needs_login)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            (notice.endpointTag + notificationId).hashCode(),
            TailscaleRouteIntent.forNotice(context, notice),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, TAILSCALE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_active)
            .setContentTitle(repository.getString(Res.string.tailscale_with_tag, notice.endpointTag))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        synchronized(postedTags) { postedTags += notice.endpointTag }
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(context).notify(notice.endpointTag, notificationId, notification)
    }
}
