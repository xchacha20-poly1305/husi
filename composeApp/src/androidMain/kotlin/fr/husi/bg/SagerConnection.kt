package fr.husi.bg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import fr.husi.Action
import fr.husi.Key
import fr.husi.database.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Lifecycle-only binder to :bg. State / speed / alerts arrive over gRPC via
 * [ServiceEventMirror]; this connection exists so BIND_AUTO_CREATE starts and
 * keeps the background process alive. QuickToggle observes
 * [BackendState.connected] through the same connection.
 */
class SagerConnection(
    private var connectionId: Int,
    private var listenForDeath: Boolean = false,
    private val mirror: ServiceEventMirror = GlobalContext.get().get(),
) : ServiceConnection, IBinder.DeathRecipient {

    companion object {
        val serviceClass
            get() = when (DataStore.serviceMode) {
                Key.MODE_PROXY -> ProxyService::class
                Key.MODE_VPN -> VpnService::class
                else -> throw UnknownError()
            }.java

        const val CONNECTION_ID_SHORTCUT = 0
        const val CONNECTION_ID_TILE = 1
        const val CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND = 2
        const val CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND = 3
    }

    private var connectionActive = false
    private var appContext: Context? = null
    private var reconnectAttempted = false
    private var binder: IBinder? = null
    private var scope: CoroutineScope? = null
    private var mirrorJob: Job? = null

    fun updateConnectionId(id: Int) {
        connectionId = id
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        this.binder = binder
        if (listenForDeath) binder.linkToDeath(this, 0)
        cancelMirror()
        val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = connectionScope
        mirrorJob = connectionScope.launch { mirror.events.collect() }
        BackendState.setConnected(true)
        reconnectAttempted = false
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        BackendState.setConnected(false)
        cancelMirror()
        binder = null
        resetStatus()
        tryReconnect()
    }

    override fun binderDied() {
        BackendState.setConnected(false)
        cancelMirror()
        binder = null
        resetStatus()
        tryReconnect()
    }

    fun connect(context: Context) {
        appContext = context.applicationContext
        reconnectAttempted = false
        if (connectionActive && binder != null) return
        connectionActive = true
        val intent = Intent(appContext, serviceClass).setAction(Action.SERVICE)
        appContext!!.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    fun disconnect(context: Context) {
        if (connectionActive) try {
            context.unbindService(this)
        } catch (_: IllegalArgumentException) {
        }
        connectionActive = false
        reconnectAttempted = false
        if (listenForDeath) try {
            binder?.unlinkToDeath(this, 0)
        } catch (_: NoSuchElementException) {
        }
        cancelMirror()
        binder = null
        BackendState.setConnected(false)
        resetStatus()
    }

    fun reconnect(context: Context) {
        disconnect(context)
        connect(context)
    }

    private fun cancelMirror() {
        mirrorJob?.cancel()
        mirrorJob = null
        scope?.cancel()
        scope = null
    }

    private fun resetStatus() {
        DataStore.serviceState = ServiceState.Idle
        BackendState.reset()
    }

    private fun tryReconnect() {
        val appContext = appContext ?: return
        if (!connectionActive || binder != null || reconnectAttempted) return
        reconnectAttempted = true
        val intent = Intent(appContext, serviceClass).setAction(Action.SERVICE)
        appContext.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }
}
