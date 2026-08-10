package fr.husi.bg

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Relays "a second instance tried to launch" signals from the single-instance
 * watcher thread to the composition, which reacts by opening the main window.
 */
object InstanceRestoreBus {

    private val restoreEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restores: SharedFlow<Unit> = restoreEvents

    fun fire() {
        restoreEvents.tryEmit(Unit)
    }
}
