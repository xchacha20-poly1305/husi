package fr.husi.bg

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.service.quicksettings.TileService
import androidx.core.content.getSystemService
import fr.husi.Action
import fr.husi.AlertType
import fr.husi.aidl.IServiceControlStub
import fr.husi.aidl.IServiceObserver
import fr.husi.aidl.SpeedDisplayData
import fr.husi.bg.proto.ProxyInstance
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.ktx.Logs
import fr.husi.ktx.broadcastReceiver
import fr.husi.ktx.hasPermission
import fr.husi.ktx.onMainDispatcher
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnMainDispatcher
import fr.husi.ktx.showToast
import fr.husi.plugin.PluginNotFoundException
import fr.husi.repository.repo
import fr.husi.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.UnknownHostException
import fr.husi.aidl.ServiceStatus as AidlServiceStatus

class BaseService {

    interface BackendEngine {
        var proxy: ProxyInstance?

        suspend fun init(profile: ProxyEntity)

        suspend fun start(onFatal: suspend (Throwable) -> Unit)

        fun stop()

        fun resetNetwork()
    }

    private class AndroidBackendEngine(private val service: Interface) : BackendEngine {
        override var proxy: ProxyInstance? = null

        override suspend fun init(profile: ProxyEntity) {
            proxy = ProxyInstance(profile, service).also {
                it.init(service is VpnService)
            }
        }

        override suspend fun start(onFatal: suspend (Throwable) -> Unit) {
            val proxy = proxy ?: return
            proxy.processes = GuardedProcessPool {
                Logs.w(it)
                onFatal(it)
            }
            proxy.launch()
        }

        override fun stop() {
            proxy?.close()
            proxy = null
        }

        override fun resetNetwork() {
            val proxy = proxy
            if (proxy != null && proxy.isInitialized()) {
                runCatching {
                    repo.boxService?.resetNetwork()
                }
            }
        }
    }

    class Data internal constructor(val service: Interface) {
        var state = ServiceState.Stopped
        val backend = service.createBackendEngine()
        var proxy: ProxyInstance?
            get() = backend.proxy
            set(value) {
                backend.proxy = value
            }
        var notification: ServiceNotifier = NoopServiceNotifier

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
                            showToast(repo.getString(Res.string.have_reset_network))
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

        fun changeState(s: ServiceState, message: String? = null) {
            if (state == s && message == null) return
            state = s
            DataStore.serviceState = s
            BackendState.updateState(s, proxy?.displayProfileName)
            binder.notifyState()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val context = service as Context
                TileService.requestListeningState(
                    context,
                    ComponentName(context, fr.husi.bg.TileService::class.java),
                )
            }
        }

        fun resetNetwork() {
            backend.resetNetwork()
        }
    }

    class Binder(private var data: Data? = null) : IServiceControlStub(), CoroutineScope,
        AutoCloseable {
        override val coroutineContext = Dispatchers.Main.immediate + Job()
        private val observers = RemoteCallbackList<IServiceObserver>()

        override fun getStatus(): AidlServiceStatus {
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

        private fun currentStatus(): AidlServiceStatus {
            val data = data ?: return AidlServiceStatus()
            val state = data.state
            return AidlServiceStatus(
                state = state.ordinal,
                profileName = data.proxy?.displayProfileName,
                started = state.started,
                connected = state.connected,
            )
        }

        private fun notifyObservers(block: (IServiceObserver) -> Unit) = launch {
            val count = observers.beginBroadcast()
            try {
                for (index in 0 until count) {
                    try {
                        block(observers.getBroadcastItem(index))
                    } catch (_: RemoteException) {
                    }
                }
            } finally {
                observers.finishBroadcast()
            }
        }
    }

    interface Interface {
        val data: Data
        val tag: String
        fun createBackendEngine(): BackendEngine = AndroidBackendEngine(this)
        fun createNotifier(profileName: String): ServiceNotifier = NoopServiceNotifier

        fun onBind(intent: Intent): IBinder? = if (intent.action == Action.SERVICE) {
            data.binder
        } else {
            null
        }

        fun reload() {
            if (DataStore.selectedProxy == 0L) {
                stopRunner(false, runBlocking { repo.getString(Res.string.profile_empty) })
            }

            val s = data.state
            when {
                s == ServiceState.Stopped -> startRunner()
                s.canStop -> stopRunner(true)
                else -> Logs.w("Illegal state $s when invoking use")
            }
        }

        suspend fun startProcesses() {
            data.backend.start { throwable ->
                stopRunner(false, throwable.readableMessage)
            }
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
            data.backend.stop()
            wakeLock?.apply {
                release()
                wakeLock = null
            }
            runOnDefaultDispatcher {
                DefaultNetworkMonitor.stop()
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null) {
            ServiceRegistry.baseService = null
            ServiceRegistry.vpnService = null

            if (data.state == ServiceState.Stopping) return
            data.notification.destroy()
            data.notification = NoopServiceNotifier
            this as Service

            data.changeState(ServiceState.Stopping)

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
                }

                // change the state
                data.changeState(ServiceState.Stopped, msg)
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
                data.notification.onWakeLock(true)
            } else {
                data.notification.onWakeLock(false)
            }
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            ServiceRegistry.baseService = this

            val data = data
            if (data.state != ServiceState.Stopped) return Service.START_NOT_STICKY
            val profile = runBlocking { SagerDatabase.proxyDao.getById(DataStore.selectedProxy) }
            this as Context
            if (profile == null) { // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotifier("")
                stopRunner(false, runBlocking { repo.getString(Res.string.profile_empty) })
                return Service.START_NOT_STICKY
            }

            setBootReceiverEnabled(DataStore.persistAcrossReboot)
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
                        Context.RECEIVER_NOT_EXPORTED,
                    )
                } else {
                    registerReceiver(
                        data.receiver,
                        filter,
                        "$packageName.SERVICE",
                        null,
                    )
                }
                data.closeReceiverRegistered = true
            }

            data.changeState(ServiceState.Connecting)
            runOnMainDispatcher {
                try {
                    data.notification = createNotifier(profile.displayNameForService())

                    Executable.killAll()    // clean up old processes
                    preInit()
                    data.backend.init(profile)
                    DataStore.currentProfile = profile.id

                    startProcesses()
                    data.changeState(ServiceState.Connected)

                    lateInit()
                } catch (_: CancellationException) { // if the job was cancelled, it is canceller's responsibility to call stopRunner
                } catch (e: UnknownHostException) {
                    Logs.e(e)
                    stopRunner(false, repo.getString(Res.string.invalid_server))
                } catch (e: PluginNotFoundException) {
                    showToast(e.readableMessage)
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
                        false,
                        "${repo.getString(Res.string.service_failed)}: ${exc.readableMessage}",
                    )
                } finally {
                    data.connectingJob = null
                }
            }
            return Service.START_NOT_STICKY
        }
    }

}
