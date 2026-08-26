package fr.husi.core

import fr.husi.bg.ServiceState
import fr.husi.proto.daemon.ServiceStatus

/**
 * What a UI's state machine has to do so it agrees with the core again.
 */
enum class CoreStateReconciliation {
    Adopt,
    MarkStarting,
    Abandon,
}

/**
 * @param coreStatus What the core last reported.
 * @param localState What this UI currently shows.
 * @return `null` when the two already agree, so nothing has to happen.
 */
fun reconciliationFor(
    coreStatus: ServiceStatus.Type,
    localState: ServiceState,
): CoreStateReconciliation? = when (coreStatus) {
    ServiceStatus.Type.STARTED ->
        CoreStateReconciliation.Adopt.takeIf { !localState.connected }

    ServiceStatus.Type.STARTING ->
        CoreStateReconciliation.MarkStarting.takeIf { !localState.started }

    ServiceStatus.Type.STOPPING -> null

    ServiceStatus.Type.IDLE, ServiceStatus.Type.FATAL ->
        CoreStateReconciliation.Abandon.takeIf { localState.canStop }

    ServiceStatus.Type.UNRECOGNIZED -> null
}
