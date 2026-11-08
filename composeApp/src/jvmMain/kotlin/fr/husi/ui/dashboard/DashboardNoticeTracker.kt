package fr.husi.ui.dashboard

import androidx.compose.ui.util.fastCoerceAtLeast
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class DashboardNoticeTracker(
    noticeCounts: List<Flow<Map<String, Int>>>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private companion object {
        const val SUBSCRIPTION_STOP_TIMEOUT_MILLIS = 5_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val seenCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val currentCounts: StateFlow<Map<String, Int>> =
        combine(noticeCounts) { countMaps ->
            buildMap {
                for (counts in countMaps) putAll(counts)
            }
        }.onEach { current ->
            // Forget what was seen of a notice once its count drops, so it counts again from there.
            seenCounts.update { seen ->
                seen.mapNotNull { (key, seenCount) ->
                    current[key]?.let { key to minOf(seenCount, it) }
                }.toMap()
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyMap())

    val unreadCount: StateFlow<Int> =
        combine(currentCounts, seenCounts) { current, seen ->
            current.entries.sumOf { (key, count) ->
                (count - (seen[key] ?: 0)).fastCoerceAtLeast(0)
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MILLIS),
            initialValue = 0,
        )

    fun markAllRead() {
        seenCounts.value = currentCounts.value
    }
}

fun Set<String>.asNoticeCounts(): Map<String, Int> = associateWith { 1 }
