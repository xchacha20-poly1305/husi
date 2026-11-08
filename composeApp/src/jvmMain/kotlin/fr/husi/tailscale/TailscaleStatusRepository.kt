package fr.husi.tailscale

import fr.husi.core.remote.CoreTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TailscaleSnapshot(
    val target: CoreTarget? = null,
    val endpoints: List<TailscaleEndpointState> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TailscaleStatusRepository(
    targets: StateFlow<CoreTarget?>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private companion object {
        const val SUBSCRIPTION_STOP_TIMEOUT_MILLIS = 5_000L
    }

    val snapshot: StateFlow<TailscaleSnapshot> = targets
        .flatMapLatest { target ->
            target?.client?.subscribeTailscaleStatus()?.map { update ->
                TailscaleSnapshot(target, update.toEndpointStates())
            } ?: flowOf(TailscaleSnapshot())
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MILLIS),
            initialValue = TailscaleSnapshot(),
        )
}
