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
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.aidl.LogItem
import io.nekohasekai.sagernet.aidl.LogItemList
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

@Immutable
data class ServiceStatus(
    val state: BaseService.State = BaseService.State.Idle,
    val profileName: String? = null,
    val speed: SpeedDisplayData? = null,
)

@Immutable
data class MissingPlugin(
    val profileName: String,
    val pluginName: String,
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

    private val _service = MutableStateFlow<ISagerNetService?>(null)
    val service = _service.asStateFlow()

    private val _logChannel = Channel<List<LogItem>>(capacity = 1)
    val logLine = _logChannel.receiveAsFlow()

    fun clearLogBuffer() {
        while (_logChannel.tryReceive().isSuccess) { }
    }

    private val _missingPlugin = MutableSharedFlow<MissingPlugin>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val missingPlugin = _missingPlugin.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private var connectionActive = false
    private var callbackRegistered = false

    private val serviceCallback = object : ISagerNetServiceCallback.Stub() {
        override fun stateChanged(state: Int, profileName: String?, msg: String?) {
            if (state < 0) return // skip private
            val s = BaseService.State.entries[state]
            DataStore.serviceState = s
            _status.value = ServiceStatus(s, profileName)
            if (msg != null) _errorMessage.tryEmit(msg)
        }

        override fun cbSpeedUpdate(stats: SpeedDisplayData) {
            _status.update {
                it.copy(speed = stats)
            }
        }

        override fun newLogs(lines: LogItemList) {
            _logChannel.trySend(lines.list)
        }

        override fun missingPlugin(profileName: String, pluginName: String) {
            _missingPlugin.tryEmit(MissingPlugin(profileName, pluginName))
        }
    }

    private var binder: IBinder? = null

    fun updateConnectionId(id: Int) {
        connectionId = id
        try {
            _service.value?.registerCallback(serviceCallback, id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        this.binder = binder
        val service = ISagerNetService.Stub.asInterface(binder)!!
        try {
            if (listenForDeath) binder.linkToDeath(this, 0)
            check(!callbackRegistered)
            service.registerCallback(serviceCallback, connectionId)
            callbackRegistered = true
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        _service.value = service
        _status.update {
            it.copy(speed = null)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        unregisterCallback()
        _service.value = null
        binder = null
    }

    override fun binderDied() {
        _service.value = null
        callbackRegistered = false
    }

    private fun unregisterCallback() {
        val service = _service.value
        if (service != null && callbackRegistered) try {
            service.unregisterCallback(serviceCallback)
        } catch (_: RemoteException) {
        }
        callbackRegistered = false
    }

    fun connect(context: Context) {
        if (connectionActive) return
        connectionActive = true
        val intent = Intent(context, serviceClass).setAction(Action.SERVICE)
        context.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    fun disconnect(context: Context) {
        unregisterCallback()
        if (connectionActive) try {
            context.unbindService(this)
        } catch (_: IllegalArgumentException) {
        }   // ignore
        connectionActive = false
        if (listenForDeath) try {
            binder?.unlinkToDeath(this, 0)
        } catch (_: NoSuchElementException) {
        }
        binder = null
        _service.value = null
        _status.value = ServiceStatus()
    }

    fun reconnect(context: Context) {
        disconnect(context)
        connect(context)
    }
}
