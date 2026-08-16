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
import fr.husi.resources.auth_required
import fr.husi.vpn.VpnAuthPendingNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.context.GlobalContext

internal class VpnAuthNotificationWatcher(
    private val notificationId: Int,
    private val channelId: String,
    private val title: StringResource,
    private val logLabel: String,
    private val pending: CoreClient.() -> Flow<VpnAuthPendingNotice?>,
) {
    private var scope: CoroutineScope? = null
    private val pendingAuth = MutableStateFlow<VpnAuthPendingNotice?>(null)

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
        val coreClient: CoreClient = GlobalContext.get().get()
        watchScope.launch {
            try {
                coreClient.pending().collect { pendingAuth.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w(logLabel, e)
            }
        }
    }

    fun stop(context: Context) {
        scope?.cancel()
        scope = null
        pendingAuth.value = null
        NotificationManagerCompat.from(context.applicationContext).cancel(notificationId)
    }

    private suspend fun updateNotification(context: Context, pending: VpnAuthPendingNotice?) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (pending == null) {
            notificationManager.cancel(notificationId)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return
        }
        val repository = resolveRepository()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.vpn_key)
            .setContentTitle(repository.getString(title))
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
        notificationManager.notify(notificationId, builder.build())
    }
}
