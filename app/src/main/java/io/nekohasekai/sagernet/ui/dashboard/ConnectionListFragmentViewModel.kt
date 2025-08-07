package io.nekohasekai.sagernet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal data class ConnectionListFragmentUiState(
    val sortMode: Int = DataStore.trafficSortMode,
    val connections: List<Connection> = emptyList(),
)

@OptIn(ExperimentalAtomicApi::class)
internal class ConnectionListFragmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionListFragmentUiState())
    val uiState = _uiState.asStateFlow()

    private var service: ISagerNetService? = null
    private var job: Job? = null

    fun initialize(service: ISagerNetService) {
        this.service = service
        job?.cancel()
        job = viewModelScope.launch {
            val interval = DataStore.speedInterval.takeIf { it > 0 }?.toLong() ?: 1000L
            while (isActive) {
                updateState()
                delay(interval)
            }
        }
    }

    private suspend fun updateState() {
        val connections = service?.queryConnections()
            ?.connections?.asSequence()
            ?.let {
                val query = query
                if (query.isNullOrBlank()) {
                    it
                } else it.filter { conn ->
                    conn.inbound.contains(query)
                            || conn.network.contains(query)
                            || conn.start.contains(query)
                            || conn.src.contains(query)
                            || conn.dst.contains(query)
                            || conn.host.contains(query)
                            || conn.matchedRule.contains(query)
                            || conn.outbound.contains(query)
                            || conn.chain.contains(query)
                            || conn.protocol?.contains(query) == true
                            || conn.process?.contains(query) == true
                }
            }
            ?.sortedWith(comparator.load())
            ?: return
        _uiState.emit(_uiState.value.copy(connections = connections.toList()))
    }

    fun stop() {
        job?.cancel()
        job = null
        service = null
    }

    fun updateSortMode(mode: Int) = runOnDefaultDispatcher {
        comparator.store(
            createConnectionComparator(
                mode,
                DataStore.trafficDescending,
            )
        )
        DataStore.trafficSortMode = mode
    }

    fun setDescending(isDescending: Boolean) = runOnDefaultDispatcher {
        comparator.store(
            createConnectionComparator(
                DataStore.trafficSortMode,
                isDescending,
            )
        )
        DataStore.trafficDescending = isDescending
    }

    private val comparator = AtomicReference(
        createConnectionComparator(
            DataStore.trafficSortMode,
            DataStore.trafficDescending,
        )
    )

    private fun createConnectionComparator(
        mode: Int,
        isDescending: Boolean,
    ): Comparator<Connection> {
        val primarySelector: (Connection) -> Comparable<*> = when (mode) {
            TrafficSortMode.START -> Connection::start
            TrafficSortMode.INBOUND -> Connection::inbound
            TrafficSortMode.SRC -> Connection::src
            TrafficSortMode.DST -> Connection::dst
            TrafficSortMode.UPLOAD -> Connection::uploadTotal
            TrafficSortMode.DOWNLOAD -> Connection::downloadTotal
            TrafficSortMode.MATCHED_RULE -> Connection::matchedRule
            else -> throw IllegalArgumentException("Unsupported sort mode: $mode")
        }

        return if (isDescending) {
            compareByDescending(primarySelector).thenByDescending(Connection::uuid)
        } else {
            compareBy(primarySelector).thenBy(Connection::uuid)
        }
    }

    private val mQuery = AtomicReference<String?>(null)
    var query
        get() = mQuery.load()
        set(value) = mQuery.store(value)
}