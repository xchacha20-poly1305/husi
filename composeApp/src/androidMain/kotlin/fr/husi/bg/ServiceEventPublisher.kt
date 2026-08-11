package fr.husi.bg

import fr.husi.core.ServiceEvent
import fr.husi.core.toProto
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository

/**
 * :bg-side publisher for [fr.husi.core.CoreClient.subscribeServiceEvents].
 * Serializes husi lifecycle / speed / alert events and pushes them through
 * the bound Go host so the UI process can mirror them without AIDL.
 */
object ServiceEventPublisher {

    fun publishState(state: ServiceState, profileName: String?) {
        publish(ServiceEvent.State(state, profileName).toProto().toByteArray())
    }

    fun publishSpeed(stats: SpeedStats) {
        publish(ServiceEvent.Speed(stats).toProto().toByteArray())
    }

    fun publishAlert(alert: ServiceAlert) {
        publish(ServiceEvent.Alert(alert).toProto().toByteArray())
    }

    private fun publish(bytes: ByteArray) {
        try {
            resolveRepository().boxService?.publishServiceEvent(bytes)
        } catch (e: Exception) {
            Logs.w("publish service event", e)
        }
    }
}
