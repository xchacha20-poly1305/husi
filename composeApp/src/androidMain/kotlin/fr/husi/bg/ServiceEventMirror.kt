package fr.husi.bg

import fr.husi.core.CoreClient
import fr.husi.core.ServiceEvent
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration.Companion.seconds

/**
 * UI-process mirror of [CoreClient.subscribeServiceEvents]. Collects the stream
 * and feeds [DataStore]/[BackendState] the same way the old AIDL observer did.
 * Side effects run once via [onEach], regardless of collector count. The stream
 * is shared only while someone is subscribed.
 */
class ServiceEventMirror(coreClient: CoreClient, scope: CoroutineScope) {
    val events: SharedFlow<ServiceEvent> = coreClient.subscribeServiceEvents()
        .onEach(::apply)
        .retryWhen { e, _ ->
            Logs.w("service event stream", e)
            delay(1.seconds)
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5.seconds.inWholeMilliseconds))

    private fun apply(event: ServiceEvent) {
        when (event) {
            is ServiceEvent.State -> {
                DataStore.serviceState = event.state
                BackendState.updateState(event.state, event.profileName)
            }
            is ServiceEvent.Speed -> BackendState.updateSpeed(event.stats)
            is ServiceEvent.Alert -> BackendState.emitAlert(event.alert)
        }
    }
}
