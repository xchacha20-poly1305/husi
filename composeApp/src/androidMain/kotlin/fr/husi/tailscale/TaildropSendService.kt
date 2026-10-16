package fr.husi.tailscale

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.husi.lib.R
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.taildrop_notification_others
import fr.husi.resources.taildrop_sending
import fr.husi.resources.taildrop_to
import fr.husi.ui.tailscale.progressFraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class TaildropSendService : Service() {

    companion object {
        const val CHANNEL_ID = "service-taildrop-send"
        private const val NOTIFICATION_ID = 8
        private const val PROGRESS_MAX = 100
        private const val ACTION_CANCEL = "fr.husi.tailscale.TAILDROP_CANCEL"

        /** Starts the service whenever [TaildropSendManager] has a running send. Main process only. */
        fun observe(context: Context) {
            val appContext = context.applicationContext
            val sendManager: TaildropSendManager = GlobalContext.get().get()
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                sendManager.sessions
                    .map { sessions -> sessions.any { session -> !session.finished } }
                    .distinctUntilChanged()
                    .collect { running ->
                        if (running) {
                            ContextCompat.startForegroundService(
                                appContext,
                                Intent(appContext, TaildropSendService::class.java),
                            )
                        }
                    }
            }
        }
    }

    private val sendManager: TaildropSendManager = GlobalContext.get().get()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            sendManager.cancelAll()
        }
        if (updateJob == null) {
            updateJob = scope.launch {
                sendManager.sessions.collect(::update)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun update(sessions: List<TaildropSendSession>) {
        val running = sessions.filterNot { session -> session.finished }
        startForeground(NOTIFICATION_ID, buildNotification(running))
        if (running.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun buildNotification(running: List<TaildropSendSession>): Notification {
        val repository = resolveRepository()
        val target = running.firstOrNull()?.let { session ->
            repository.getString(Res.string.taildrop_to, session.peerName)
        }
        val text = if (target != null && running.size > 1) {
            repository.getString(Res.string.taildrop_notification_others, target, running.size - 1)
        } else {
            target
        }
        val fractions = running.map { session -> session.progressFraction() }
        val knownFractions = fractions.filterNotNull()
        val indeterminate = knownFractions.isEmpty() || knownFractions.size < fractions.size
        val progress = if (knownFractions.isEmpty()) {
            0
        } else {
            (knownFractions.average() * PROGRESS_MAX).toInt()
        }
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TaildropSendService::class.java)
                .setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_active)
            .setContentTitle(repository.getString(Res.string.taildrop_sending))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, progress, indeterminate)
            .addAction(0, repository.getString(Res.string.cancel), cancelIntent)
            .build()
    }
}
