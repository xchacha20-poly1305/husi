package fr.husi.bg

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServiceState(
    val canStop: Boolean = false,
    val started: Boolean = false,
    val connected: Boolean = false,
) {
    Idle,
    Connecting(canStop = true, started = true, connected = false),
    Connected(canStop = true, started = true, connected = true),
    Stopping,
    Stopped,
}

data class SpeedStats(
    // Bytes per second
    val txRateProxy: Long = 0L,
    val rxRateProxy: Long = 0L,
    val txRateDirect: Long = 0L,
    val rxRateDirect: Long = 0L,

    // Bytes for the current session
    // Outbound "bypass" usage is not counted
    val txTotal: Long = 0L,
    val rxTotal: Long = 0L,
)

data class ServiceStatus(
    val state: ServiceState = ServiceState.Idle,
    val profileName: String? = null,
    val speed: SpeedStats? = null,
)

data class Alert(
    val type: Int,
    val message: String,
)

object BackendState {
    private val _status = MutableStateFlow(ServiceStatus())
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _alerts = MutableSharedFlow<Alert>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    fun updateState(state: ServiceState, profileName: String? = null) {
        _status.value = ServiceStatus(state, profileName, _status.value.speed)
    }

    fun updateSpeed(speed: SpeedStats?) {
        _status.value = _status.value.copy(speed = speed)
    }

    fun emitAlert(type: Int, message: String) {
        _alerts.tryEmit(Alert(type, message))
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun reset() {
        _connected.value = false
        _status.value = ServiceStatus()
    }
}
