package io.nekohasekai.sagernet.bg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import androidx.compose.runtime.Immutable
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.aidl.IServiceControl
import io.nekohasekai.sagernet.aidl.IServiceObserver
import io.nekohasekai.sagernet.aidl.ServiceStatus as RemoteServiceStatus
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class ServiceStatus(
    val state: BaseService.State = BaseService.State.Idle,
    val profileName: String? = null,
    val speed: SpeedDisplayData? = null,
)

@Immutable
data class Alert(
    val type: Int,
    val message: String,
)

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

    private val _status = MutableStateFlow(ServiceStatus())
    val status = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    private val _alert = MutableSharedFlow<Alert>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val alert: SharedFlow<Alert> = _alert.asSharedFlow()

    private var connectionActive = false
    private var appContext: Context? = null
    private var reconnectAttempted = false
    private var binder: IBinder? = null
    private var service: IServiceControl? = null
    private val observer = object : IServiceObserver.Stub() {
        override fun onState(status: RemoteServiceStatus) {
            handleStatus(status)
        }

        override fun onSpeed(speed: SpeedDisplayData) {
            updateSpeed(speed)
        }

        override fun onAlert(type: Int, message: String) {
            _alert.tryEmit(Alert(type, message))
        }
    }

    fun updateConnectionId(id: Int) {
        connectionId = id
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        this.binder = binder
        if (listenForDeath) binder.linkToDeath(this, 0)
        service = IServiceControl.Stub.asInterface(binder)
        try {
            service?.registerObserver(observer)
            service?.getStatus()?.let { handleStatus(it) }
        } catch (_: RemoteException) {
        }
        _connected.value = true
        reconnectAttempted = false
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        _connected.value = false
        unregisterObserver()
        binder = null
        service = null
        resetStatus()
        tryReconnect()
    }

    override fun binderDied() {
        _connected.value = false
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
        _connected.value = false
        resetStatus()
    }

    fun reconnect(context: Context) {
        disconnect(context)
        connect(context)
    }

    fun updateSpeed(speed: SpeedDisplayData?) {
        _status.value = _status.value.copy(speed = speed)
    }

    fun updateState(state: BaseService.State, profileName: String? = null) {
        DataStore.serviceState = state
        _status.value = ServiceStatus(state, profileName, _status.value.speed)
    }

    private fun handleStatus(status: RemoteServiceStatus) {
        val state = BaseService.State.entries.getOrNull(status.state) ?: BaseService.State.Idle
        DataStore.serviceState = state
        _status.value = ServiceStatus(state, status.profileName, _status.value.speed)
    }

    private fun unregisterObserver() {
        val service = service ?: return
        try {
            service.unregisterObserver(observer)
        } catch (_: RemoteException) {
        }
    }

    private fun resetStatus() {
        DataStore.serviceState = BaseService.State.Idle
        _status.value = ServiceStatus()
    }

    private fun tryReconnect() {
        val appContext = appContext ?: return
        if (!connectionActive || binder != null || reconnectAttempted) return
        reconnectAttempted = true
        val intent = Intent(appContext, serviceClass).setAction(Action.SERVICE)
        appContext.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }
}
