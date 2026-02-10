package fr.husi.bg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import fr.husi.Action
import fr.husi.Key
import fr.husi.aidl.IServiceControl
import fr.husi.aidl.IServiceControlStub
import fr.husi.aidl.IServiceObserverStub
import fr.husi.aidl.SpeedDisplayData
import fr.husi.database.DataStore
import fr.husi.aidl.ServiceStatus as RemoteServiceStatus

class SagerConnection(
    private var connectionId: Int,
    private var listenForDeath: Boolean = false,
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
    private var service: IServiceControl? = null
    private val observer = object : IServiceObserverStub() {
        override fun onState(status: RemoteServiceStatus) {
            handleStatus(status)
        }

        override fun onSpeed(speed: SpeedDisplayData) {
            updateSpeed(speed)
        }

        override fun onAlert(type: Int, message: String) {
            BackendState.emitAlert(type, message)
        }
    }

    fun updateConnectionId(id: Int) {
        connectionId = id
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        this.binder = binder
        if (listenForDeath) binder.linkToDeath(this, 0)
        service = IServiceControlStub.asInterface(binder)
        try {
            service?.registerObserver(observer)
            service?.getStatus()?.let { handleStatus(it) }
        } catch (_: RemoteException) {
        }
        BackendState.setConnected(true)
        reconnectAttempted = false
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        BackendState.setConnected(false)
        unregisterObserver()
        binder = null
        service = null
        resetStatus()
        tryReconnect()
    }

    override fun binderDied() {
        BackendState.setConnected(false)
        unregisterObserver()
        binder = null
        service = null
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
        unregisterObserver()
        binder = null
        service = null
        BackendState.setConnected(false)
        resetStatus()
    }

    fun reconnect(context: Context) {
        disconnect(context)
        connect(context)
    }

    fun updateSpeed(speed: SpeedDisplayData?) {
        BackendState.updateSpeed(speed?.toStats())
    }

    fun updateState(state: ServiceState, profileName: String? = null) {
        DataStore.serviceState = state
        BackendState.updateState(state, profileName)
    }

    private fun handleStatus(status: RemoteServiceStatus) {
        val state = ServiceState.entries.getOrNull(status.state) ?: ServiceState.Idle
        DataStore.serviceState = state
        BackendState.updateState(state, status.profileName)
    }

    private fun unregisterObserver() {
        val service = service ?: return
        try {
            service.unregisterObserver(observer)
        } catch (_: RemoteException) {
        }
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

private fun SpeedDisplayData.toStats() = SpeedStats(
    txRateProxy = txRateProxy,
    rxRateProxy = rxRateProxy,
    txRateDirect = txRateDirect,
    rxRateDirect = rxRateDirect,
    txTotal = txTotal,
    rxTotal = rxTotal,
)
