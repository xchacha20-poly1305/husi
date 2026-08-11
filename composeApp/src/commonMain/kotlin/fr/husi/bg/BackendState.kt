package fr.husi.bg

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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

object BackendState {
    val status: StateFlow<ServiceStatus>
        field = MutableStateFlow(ServiceStatus())

    val connected: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val alerts: SharedFlow<ServiceAlert>
        field = MutableSharedFlow<ServiceAlert>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    fun updateState(state: ServiceState, profileName: String? = null) {
        status.value = ServiceStatus(state, profileName, status.value.speed)
    }

    fun updateSpeed(speed: SpeedStats?) {
        status.value = status.value.copy(speed = speed)
    }

    fun emitAlert(alert: ServiceAlert) {
        alerts.tryEmit(alert)
    }

    fun setConnected(value: Boolean) {
        connected.value = value
    }

    fun reset() {
        connected.value = false
        status.value = ServiceStatus()
    }
}
