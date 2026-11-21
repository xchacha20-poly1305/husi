package fr.husi.bg

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.husi.Action
import fr.husi.compose.theme.getPrimaryColor
import fr.husi.database.DataStore
import fr.husi.database.preference.getBlocking
import fr.husi.ktx.runOnMainDispatcher
import fr.husi.lib.R
import fr.husi.repository.resolveAndroidRepository
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_switch
import fr.husi.resources.forward_success
import fr.husi.resources.reset_connections
import fr.husi.resources.speed
import fr.husi.resources.speed_detail
import fr.husi.resources.stop
import fr.husi.resources.traffic
import fr.husi.ui.SwitchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * User can customize visibility of notification since Android 8.
 * The default visibility:
 *
 * Android 8.x: always visible due to system limitations
 * VPN:         always invisible because of VPN notification/icon
 * Other:       always visible
 *
 * See also: https://github.com/aosp-mirror/platform_frameworks_base/commit/070d142993403cc2c42eca808ff3fafcee220ac4
 */
class ServiceNotification(
    private val service: BaseService.Interface, title: String,
    channel: String, visible: Boolean = false,
) : BroadcastReceiver(), ServiceNotifier {
    companion object {
        const val NOTIFICATION_ID = 1
        const val FLAGS = PendingIntent.FLAG_IMMUTABLE
    }

    @Volatile
    private var listenPostSpeed = true

    @Volatile
    private var destroyed = false

    override fun canPostSpeed(): Boolean = !destroyed && listenPostSpeed

    override suspend fun onSpeed(speed: SpeedStats) {
        postNotificationSpeedUpdate(speed)
    }

    override suspend fun onWakeLock(acquired: Boolean) {
        postNotificationWakeLockStatus(acquired)
    }

    suspend fun postNotificationSpeedUpdate(stats: SpeedStats) {
        if (destroyed) return
        val context = service as Context
        val repo = resolveRepository()
        val contentText = if (showDirectSpeed) {
            repo.getString(
                Res.string.speed_detail,
                repo.getString(
                    Res.string.speed,
                    Formatter.formatFileSize(context, stats.txRateProxy),
                ),
                repo.getString(
                    Res.string.speed,
                    Formatter.formatFileSize(context, stats.rxRateProxy),
                ),
                repo.getString(
                    Res.string.speed,
                    Formatter.formatFileSize(context, stats.txRateDirect),
                ),
                repo.getString(
                    Res.string.speed,
                    Formatter.formatFileSize(context, stats.rxRateDirect),
                ),
            )
        } else {
            repo.getString(
                Res.string.traffic,
                repo.getString(
                    Res.string.speed,
                    Formatter.formatFileSize(context, stats.txRateProxy),
                ),
                repo.getString(
                    Res.string.speed,
                    Formatter.formatFileSize(context, stats.rxRateProxy),
                ),
            )
        }
        val subText = repo.getString(
            Res.string.traffic,
            Formatter.formatFileSize(context, stats.txTotal),
            Formatter.formatFileSize(context, stats.rxTotal),
        )
        useBuilder {
            if (showDirectSpeed) {
                it.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            }
            it.setContentText(contentText)
            it.setSubText(subText)
        }
        update()
    }

    override suspend fun onTitle(title: String) {
        if (destroyed) return
        useBuilder {
            it.setContentTitle(title)
        }
        update()
    }

    suspend fun postNotificationWakeLockStatus(acquired: Boolean) {
        if (destroyed) return
        updateActions()
        useBuilder {
            it.priority = if (acquired) {
                NotificationCompat.PRIORITY_HIGH
            } else {
                NotificationCompat.PRIORITY_LOW
            }
        }
        update()
    }

    private val showDirectSpeed = DataStore.showDirectSpeed.getBlocking()

    private val builder = NotificationCompat.Builder(service as Context, channel)
        .setWhen(0)
        .setContentTitle(title)
        .setOnlyAlertOnce(true)
        .setContentIntent(resolveAndroidRepository().configureIntent(service))
        .setSmallIcon(R.drawable.ic_service_active)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(
            if (visible) {
                NotificationCompat.PRIORITY_LOW
            } else {
                NotificationCompat.PRIORITY_MIN
            },
        )

    private val initJob: Job

    private suspend fun useBuilder(f: suspend (NotificationCompat.Builder) -> Unit) {
        withContext(Dispatchers.Main) {
            f(builder)
        }
    }

    init {
        service as Context

        builder.color = service.getPrimaryColor()

        service.registerReceiver(
            this,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )

        (service as Service).startForeground(NOTIFICATION_ID, builder.build())

        initJob = runOnMainDispatcher {
            val ticker = resolveRepository().getString(Res.string.forward_success)
            useBuilder {
                it.setTicker(ticker)
            }
            updateActions()
            update()
        }
    }

    private suspend fun updateActions() {
        service as Context
        val repo = resolveRepository()
        val stopLabel = repo.getString(Res.string.stop)
        val switchLabel = repo.getString(Res.string.action_switch)
        val resetLabel = repo.getString(Res.string.reset_connections)
        useBuilder {
            it.clearActions()

            val closeAction = NotificationCompat.Action.Builder(
                0,
                stopLabel,
                PendingIntent.getBroadcast(
                    service, 0, Intent(Action.CLOSE).setPackage(service.packageName), FLAGS,
                ),
            ).setShowsUserInterface(false).build()
            it.addAction(closeAction)

            val switchAction = NotificationCompat.Action.Builder(
                0,
                switchLabel,
                PendingIntent.getActivity(
                    service, 0, Intent(service, SwitchActivity::class.java), FLAGS,
                ),
            )
                .setShowsUserInterface(false)
                .build()
            it.addAction(switchAction)

            val resetUpstreamAction = NotificationCompat.Action.Builder(
                0,
                resetLabel,
                PendingIntent.getBroadcast(
                    service,
                    0,
                    Intent(Action.RESET_UPSTREAM_CONNECTIONS).setPackage(service.packageName),
                    FLAGS,
                ),
            ).setShowsUserInterface(false).build()
            it.addAction(resetUpstreamAction)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (destroyed) return
        if (service.data.state == ServiceState.Connected) {
            listenPostSpeed = intent.action == Intent.ACTION_SCREEN_ON
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun update() = useBuilder {
        if (destroyed) return@useBuilder
        NotificationManagerCompat.from(service as Service).notify(NOTIFICATION_ID, it.build())
    }

    override fun destroy() {
        destroyed = true
        listenPostSpeed = false
        initJob.cancel()
        val androidService = service as Service
        androidService.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(androidService).cancel(NOTIFICATION_ID)
        runCatching {
            androidService.unregisterReceiver(this)
        }
        runOnMainDispatcher {
            if (service.data.notification === NoopServiceNotifier || service.data.notification === this) {
                NotificationManagerCompat.from(androidService).cancel(NOTIFICATION_ID)
            }
        }
    }
}
