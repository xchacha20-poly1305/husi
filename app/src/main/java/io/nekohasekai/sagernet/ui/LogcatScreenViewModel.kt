package io.nekohasekai.sagernet.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.LogItem
import io.nekohasekai.sagernet.aidl.toList
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libcore.Client
import libcore.Libcore

@Immutable
data class LogcatUiState(
    val pause: Boolean = false,
    val searchQuery: String? = null,
    val logLevel: LogLevel = LogLevel.WARN,
    val logs: PersistentList<LogItem> = persistentListOf(),
    val errorMessage: String? = null,
)

@Immutable
enum class LogLevel() {
    PANIC,
    FATAL,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE,
}

@Stable
class LogcatScreenViewModel : ViewModel() {

    companion object {
        private const val POLL_INTERVAL = 500L
    }

    private var allLogs: PersistentList<LogItem> = persistentListOf()
    private val _uiState = MutableStateFlow(
        LogcatUiState(logLevel = LogLevel.entries.getOrNull(DataStore.logLevel) ?: LogLevel.WARN),
    )
    val uiState = _uiState.asStateFlow()
    val searchTextFieldState = TextFieldState()

    private var job: Job? = null
    private var client: Client? = null
    private var lastLogCount = 0

    init {
        viewModelScope.launch {
            snapshotFlow { searchTextFieldState.text.toString() }
                .drop(1)
                .distinctUntilChanged()
                .collect { setSearchQuery(it.ifEmpty { null }) }
        }
    }

    private fun refilterLogs(logLevel: LogLevel, query: String?): PersistentList<LogItem> {
        return allLogs.filter { item ->
            item.level <= logLevel.ordinal
                    && query?.let { item.message.contains(it, ignoreCase = true) } ?: true
        }.toPersistentList()
    }

    private fun appendLogs(newLogs: List<LogItem>) {
        if (newLogs.isEmpty()) return
        allLogs = allLogs.addAll(newLogs)
        _uiState.update { state ->
            if (state.pause) return
            val level = state.logLevel.ordinal
            val filtered = newLogs.filter { item ->
                item.level <= level && state.searchQuery?.let {
                    item.message.contains(
                        it,
                        ignoreCase = true,
                    )
                } ?: true
            }
            if (filtered.isEmpty()) return
            state.copy(logs = state.logs.addAll(filtered))
        }
    }

    fun initialize(isConnected: Boolean) {
        job?.cancel()
        client?.close()
        client = null
        allLogs = persistentListOf()
        lastLogCount = 0
        _uiState.update { it.copy(logs = persistentListOf()) }

        if (!isConnected) return

        client = try {
            Libcore.newClient()
        } catch (e: Exception) {
            Logs.w("Failed to create client: ${e.message}")
            null
        }

        job = viewModelScope.launch {
            while (isActive) {
                pollLogs()
                delay(POLL_INTERVAL)
            }
        }
    }

    private fun pollLogs() {
        if (DataStore.serviceState != BaseService.State.Connected) return
        val c = client ?: return
        try {
            val iterator = c.queryLogs() ?: return
            val logList = iterator.toList()
            val totalCount = logList.list.size
            if (totalCount > lastLogCount) {
                val newLogs = logList.list.drop(lastLogCount)
                lastLogCount = totalCount
                appendLogs(newLogs)
            }
        } catch (e: Exception) {
            Logs.w("pollLogs error: ${e.message}")
        }
    }

    override fun onCleared() {
        job?.cancel()
        client?.close()
        client = null
        super.onCleared()
    }

    fun togglePause() {
        _uiState.update { state ->
            val newPause = !state.pause
            state.copy(
                pause = newPause,
                logs = if (newPause) {
                    state.logs
                } else {
                    refilterLogs(state.logLevel, state.searchQuery)
                },
            )
        }
    }

    fun clearLog() {
        try {
            client?.clearLog()
        } catch (e: Exception) {
            Logs.w("clearLog error: ${e.message}")
        }
        allLogs = persistentListOf()
        lastLogCount = 0
        _uiState.update { it.copy(logs = persistentListOf()) }
    }

    fun setLogLevel(level: LogLevel) {
        _uiState.update { state ->
            state.copy(
                logLevel = level,
                logs = if (state.pause) {
                    state.logs
                } else {
                    refilterLogs(level, state.searchQuery)
                },
            )
        }
    }

    fun setSearchQuery(query: String?) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                logs = if (state.pause) {
                    state.logs
                } else {
                    refilterLogs(state.logLevel, query)
                },
            )
        }
    }

    fun clearSearchQuery() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

}
