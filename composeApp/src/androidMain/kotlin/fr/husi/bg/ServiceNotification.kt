package fr.husi.bg

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.husi.Action
import fr.husi.aidl.SpeedDisplayData
import fr.husi.compose.theme.getPrimaryColor
import fr.husi.database.DataStore
import fr.husi.ktx.onMainDispatcher
import fr.husi.ktx.runOnMainDispatcher
import fr.husi.lib.R
import fr.husi.repository.androidRepo
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.action_switch
import fr.husi.resources.forward_success
import fr.husi.resources.reset_connections
import fr.husi.resources.speed
import fr.husi.resources.speed_detail
import fr.husi.resources.stop
import fr.husi.resources.traffic
import fr.husi.ui.SwitchActivity
import kotlinx.coroutines.runBlocking

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
        const val notificationId = 1
        const val flags = PendingIntent.FLAG_IMMUTABLE
    }

    private var listenPostSpeed = true

    override fun canPostSpeed(): Boolean = listenPostSpeed

    override suspend fun onSpeed(speed: SpeedDisplayData) {
        postNotificationSpeedUpdate(speed)
    }

    override suspend fun onWakeLock(acquired: Boolean) {
        postNotificationWakeLockStatus(acquired)
    }

    suspend fun postNotificationSpeedUpdate(stats: SpeedDisplayData) {
        val context = service as Context
        useBuilder {
            if (showDirectSpeed) {
                val speedDetail = runBlocking {
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
                }
                it.setStyle(NotificationCompat.BigTextStyle().bigText(speedDetail))
                it.setContentText(speedDetail)
            } else {
                val speedSimple = runBlocking {
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
                it.setContentText(speedSimple)
            }
            it.setSubText(
                runBlocking {
                    repo.getString(
                        Res.string.traffic,
                        Formatter.formatFileSize(context, stats.txTotal),
                        Formatter.formatFileSize(context, stats.rxTotal),
                    )
                },
            )
        }
        update()
    }

    override suspend fun onTitle(title: String) {
        useBuilder {
            it.setContentTitle(title)
        }
        update()
    }

    suspend fun postNotificationWakeLockStatus(acquired: Boolean) {
        updateActions()
        useBuilder {
            it.priority =
                if (acquired) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
        }
        update()
    }

    private val showDirectSpeed = DataStore.showDirectSpeed

    private val builder = runBlocking {
        NotificationCompat.Builder(service as Context, channel)
            .setWhen(0)
            .setTicker(repo.getString(Res.string.forward_success))
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setContentIntent(androidRepo.configureIntent(service))
            .setSmallIcon(R.drawable.ic_service_active)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(if (visible) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
    }

    private suspend fun useBuilder(f: (NotificationCompat.Builder) -> Unit) {
        onMainDispatcher {
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

        runOnMainDispatcher {
            updateActions()
            show()
        }
    }

    private suspend fun updateActions() {
        service as Context
        useBuilder {
            it.clearActions()

            val closeAction = NotificationCompat.Action.Builder(
                0,
                runBlocking { repo.getString(Res.string.stop) },
                PendingIntent.getBroadcast(
                    service, 0, Intent(Action.CLOSE).setPackage(service.packageName), flags,
                ),
            ).setShowsUserInterface(false).build()
            it.addAction(closeAction)

            val switchAction = NotificationCompat.Action.Builder(
                0,
                runBlocking { repo.getString(Res.string.action_switch) },
                PendingIntent.getActivity(
                    service, 0, Intent(service, SwitchActivity::class.java), flags,
                ),
            ).setShowsUserInterface(false).build()
            it.addAction(switchAction)

            val resetUpstreamAction = NotificationCompat.Action.Builder(
                0,
                runBlocking { repo.getString(Res.string.reset_connections) },
                PendingIntent.getBroadcast(
                    service, 0, Intent(Action.RESET_UPSTREAM_CONNECTIONS), flags,
                ),
            ).setShowsUserInterface(false).build()
            it.addAction(resetUpstreamAction)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (service.data.state == ServiceState.Connected) {
            listenPostSpeed = intent.action == Intent.ACTION_SCREEN_ON
        }
    }


    private suspend fun show() =
        useBuilder { (service as Service).startForeground(notificationId, it.build()) }

    @SuppressLint("MissingPermission")
    private suspend fun update() = useBuilder {
        NotificationManagerCompat.from(service as Service).notify(notificationId, it.build())
    }

    override fun destroy() {
        listenPostSpeed = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (service as Service).stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            (service as Service).stopForeground(true)
        }
        service.unregisterReceiver(this)
    }
}
