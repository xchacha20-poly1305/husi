package fr.husi.bg

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.husi.ktx.hasPermission
import fr.husi.lib.R
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.openconnect_authentication
import fr.husi.resources.auth_required
import fr.husi.ui.openconnect.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.utils.LibcoreClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Watches OpenConnect endpoint status in the :bg process and raises a
 * notification while authentication is pending, so the user notices
 * even when the UI process is not running. Tapping the notification
 * just opens the app; the global auth dialog takes over from there.
 */
object OpenConnectAuthWatcher {

    private const val NOTIFICATION_ID = 3

    private class PendingAuth(val endpointTag: String, val formId: String) {
        override fun equals(other: Any?): Boolean =
            other is PendingAuth && other.endpointTag == endpointTag && other.formId == formId

        override fun hashCode(): Int = endpointTag.hashCode() * 31 + formId.hashCode()
    }

    private var scope: CoroutineScope? = null
    private val pendingAuth = MutableStateFlow<PendingAuth?>(null)

    fun start(context: Context) {
        if (scope != null) return
        val appContext = context.applicationContext
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = watchScope
        watchScope.launch {
            pendingAuth.collect { pending ->
                updateNotification(appContext, pending)
            }
        }
        LibcoreClientManager().subscribeOpenConnectStatus(watchScope) { iterator ->
            var pending: PendingAuth? = null
            while (iterator.hasNext()) {
                val status = iterator.next() ?: continue
                val form = status.authForm
                if (status.state == OPENCONNECT_STATE_AUTH_PENDING && form != null) {
                    pending = PendingAuth(status.tag, form.id)
                    break
                }
            }
            pendingAuth.value = pending
        }
    }

    fun stop(context: Context) {
        scope?.cancel()
        scope = null
        pendingAuth.value = null
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private suspend fun updateNotification(context: Context, pending: PendingAuth?) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (pending == null) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return
        }
        val repository = resolveRepository()
        val builder = NotificationCompat.Builder(context, "service-openconnect-auth")
            .setSmallIcon(R.drawable.vpn_key)
            .setContentTitle(repository.getString(Res.string.openconnect_authentication))
            .setContentText(
                "${repository.getString(Res.string.auth_required)}: ${pending.endpointTag}",
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        @SuppressLint("MissingPermission")
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
