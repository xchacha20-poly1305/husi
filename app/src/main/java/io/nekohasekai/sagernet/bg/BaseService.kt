package io.nekohasekai.sagernet.bg

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.widget.Toast
import androidx.core.content.getSystemService
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.AlertType
import io.nekohasekai.sagernet.BootReceiver
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.IServiceControl
import io.nekohasekai.sagernet.aidl.IServiceObserver
import io.nekohasekai.sagernet.aidl.ServiceStatus
import io.nekohasekai.sagernet.bg.proto.ProxyInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.broadcastReceiver
import io.nekohasekai.sagernet.ktx.hasPermission
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import libcore.Libcore
import java.net.UnknownHostException

class BaseService {

    enum class State(
        val canStop: Boolean = false,
        val started: Boolean = false,
        val connected: Boolean = false,
    ) {
        /**
         * Idle state is only used by UI and will never be returned by BaseService.
         */
        Idle,
        Connecting(true, true, false),
        Connected(true, true, true),
        Stopping,
        Stopped,
    }

    interface ExpectedException

    class Data internal constructor(val service: Interface) {
        var state = State.Stopped
        var proxy: ProxyInstance? = null
        var notification: ServiceNotification? = null

        val receiver = broadcastReceiver { ctx, intent ->
            when (intent.action) {
                Action.RELOAD -> service.reload()
                // Action.SWITCH_WAKE_LOCK -> runOnDefaultDispatcher { service.switchWakeLock() }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    val powerManager = (service as Context).getSystemService<PowerManager>()!!
                    val proxy = proxy
                    if (proxy != null && proxy.isInitialized()) {
                        if (powerManager.isDeviceIdleMode) {
                            repo.boxService?.pause()
                        } else {
                            repo.boxService?.wake()
                        }
                    }
                }

                Action.RESET_UPSTREAM_CONNECTIONS -> runOnDefaultDispatcher {
                    withTimeoutOrNull(1000L) {
                        resetNetwork()
                        onMainDispatcher {
                            collapseStatusBar(ctx)
                            Toast.makeText(ctx, R.string.have_reset_network, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }

                else -> service.stopRunner()
            }
        }

        @SuppressLint("WrongConstant")
        private fun collapseStatusBar(context: Context) {
            try {
                val statusBarManager = context.getSystemService("statusbar")
                val collapse = statusBarManager.javaClass.getMethod("collapsePanels")
                collapse.invoke(statusBarManager)
            } catch (_: Exception) {
            }
        }

        var closeReceiverRegistered = false

        val binder = Binder(this)
        var connectingJob: Job? = null

        fun changeState(s: State, message: String? = null) {
            if (state == s && message == null) return
            state = s
            DataStore.serviceState = s
            binder.notifyState()
        }

        fun resetNetwork() {
            val proxy = proxy
            if (proxy != null && proxy.isInitialized()) {
                repo.boxService?.resetNetwork()
            } else {
                Libcore.resetAllConnections()
            }
        }
    }

    class Binder(private var data: Data? = null) : IServiceControl.Stub(), CoroutineScope, AutoCloseable {
        override val coroutineContext = Dispatchers.Main.immediate + Job()
        private val observers = RemoteCallbackList<IServiceObserver>()

        override fun getStatus(): ServiceStatus {
            return currentStatus()
        }

        override fun registerObserver(observer: IServiceObserver?) {
            if (observer == null) return
            observers.register(observer)
            try {
                observer.onState(currentStatus())
            } catch (_: RemoteException) {
            }
        }

        override fun unregisterObserver(observer: IServiceObserver?) {
            if (observer == null) return
            observers.unregister(observer)
        }

        fun notifyState() {
            val status = currentStatus()
            notifyObservers { it.onState(status) }
        }

        fun notifyAlert(type: Int, message: String) {
            notifyObservers { it.onAlert(type, message) }
        }

        fun notifySpeed(speed: SpeedDisplayData) {
            notifyObservers { it.onSpeed(speed) }
        }

        override fun close() {
            observers.kill()
            cancel()
            data = null
        }

        private fun currentStatus(): ServiceStatus {
            val data = data ?: return ServiceStatus()
            val state = data.state
            return ServiceStatus(
                state = state.ordinal,
                profileName = data.proxy?.displayProfileName,
                started = state.started,
                connected = state.connected,
            )
        }

        private fun notifyObservers(block: (IServiceObserver) -> Unit) {
            val count = observers.beginBroadcast()
            for (index in 0 until count) {
                try {
                    block(observers.getBroadcastItem(index))
                } catch (_: RemoteException) {
                }
            }
            observers.finishBroadcast()
        }
    }

    interface Interface {
        val data: Data
        val tag: String
        fun createNotification(profileName: String): ServiceNotification

        fun onBind(intent: Intent): IBinder? = if (intent.action == Action.SERVICE) {
            data.binder
        } else {
            null
        }

        fun reload() {
            if (DataStore.selectedProxy == 0L) {
                stopRunner(false, (this as Context).getString(R.string.profile_empty))
            }

            val s = data.state
            when {
                s == State.Stopped -> startRunner()
                s.canStop -> stopRunner(true)
                else -> Logs.w("Illegal state $s when invoking use")
            }
        }

        suspend fun startProcesses() {
            data.proxy!!.launch()
            if (repo.boxService?.needWIFIState() == true) {
                val wifiPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Manifest.permission.ACCESS_FINE_LOCATION
                } else {
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                }
                this as Context
                if (!hasPermission(wifiPermission)) {
                    data.binder.notifyAlert(AlertType.NEED_WIFI_PERMISSION, "")
                }
            }
        }

        fun startRunner() {
            this as Context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, javaClass))
            } else {
                startService(Intent(this, javaClass))
            }
        }

        fun killProcesses() {
            data.proxy?.close()
            wakeLock?.apply {
                release()
                wakeLock = null
            }
            runOnDefaultDispatcher {
                DefaultNetworkMonitor.stop()
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null) {
            DataStore.baseService = null
            DataStore.vpnService = null

            if (data.state == State.Stopping) return
            data.notification?.destroy()
            data.notification = null
            this as Service

            data.changeState(State.Stopping)

            runOnMainDispatcher {
                data.connectingJob?.cancelAndJoin() // ensure stop connecting first
                // we use a coroutineScope here to allow clean-up in parallel
                coroutineScope {
                    killProcesses()
                    val data = data
                    if (data.closeReceiverRegistered) {
                        unregisterReceiver(data.receiver)
                        data.closeReceiverRegistered = false
                    }
                    data.proxy = null
                }

                // change the state
                data.changeState(State.Stopped, msg)
                if (!msg.isNullOrBlank()) {
                    data.binder.notifyAlert(AlertType.COMMON, msg)
                }
                // stop the service if nothing has bound to it
                if (restart) startRunner() else {
                    stopSelf()
                }
            }
        }

        // networks
        var upstreamInterfaceName: String?

        suspend fun preInit() {
            DefaultNetworkMonitor.start()
        }

        var wakeLock: PowerManager.WakeLock?
        fun acquireWakeLock()

        suspend fun lateInit() {
            wakeLock?.apply {
                release()
                wakeLock = null
            }

            if (DataStore.acquireWakeLock) {
                acquireWakeLock()
                data.notification?.postNotificationWakeLockStatus(true)
            } else {
                data.notification?.postNotificationWakeLockStatus(false)
            }
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            DataStore.baseService = this

            val data = data
            if (data.state != State.Stopped) return Service.START_NOT_STICKY
            val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
            this as Context
            if (profile == null) { // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotification("")
                stopRunner(false, getString(R.string.profile_empty))
                return Service.START_NOT_STICKY
            }

            val proxy = ProxyInstance(profile, this)
            data.proxy = proxy
            BootReceiver.enabled = DataStore.persistAcrossReboot
            if (!data.closeReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Action.RELOAD)
                    addAction(Intent.ACTION_SHUTDOWN)
                    addAction(Action.CLOSE)
                    // addAction(Action.SWITCH_WAKE_LOCK)
                    addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    addAction(Action.RESET_UPSTREAM_CONNECTIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        data.receiver,
                        filter,
                        "$packageName.SERVICE",
                        null,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    registerReceiver(
                        data.receiver,
                        filter,
                        "$packageName.SERVICE",
                        null
                    )
                }
                data.closeReceiverRegistered = true
            }

            data.changeState(State.Connecting)
            runOnMainDispatcher {
                try {
                    data.notification = createNotification(ServiceNotification.genTitle(profile))

                    Executable.killAll()    // clean up old processes
                    preInit()
                    proxy.init(data.proxy?.service is VpnService)
                    DataStore.currentProfile = profile.id

                    proxy.processes = GuardedProcessPool {
                        Logs.w(it)
                        stopRunner(false, it.readableMessage)
                    }

                    startProcesses()
                    data.changeState(State.Connected)

                    lateInit()
                } catch (_: CancellationException) { // if the job was cancelled, it is canceller's responsibility to call stopRunner
                } catch (e: UnknownHostException) {
                    Logs.e(e)
                    stopRunner(false, getString(R.string.invalid_server))
                } catch (e: PluginManager.PluginNotFoundException) {
                    Toast.makeText(this@Interface, e.readableMessage, Toast.LENGTH_SHORT).show()
                    Logs.w(e)
                    stopRunner(false, e.readableMessage)
                    data.binder.notifyAlert(AlertType.MISSING_PLUGIN, e.plugin)
                } catch (exc: Throwable) {
                    if (exc.javaClass.name.endsWith("proxyerror")) {
                        // error from golang
                        Logs.e(exc.readableMessage)
                    } else {
                        Logs.e(exc)
                    }
                    stopRunner(
                        false, "${getString(R.string.service_failed)}: ${exc.readableMessage}"
                    )
                } finally {
                    data.connectingJob = null
                }
            }
            return Service.START_NOT_STICKY
        }
    }

}
