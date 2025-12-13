package io.nekohasekai.sagernet.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.LogItem
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private var allLogs: PersistentList<LogItem> = persistentListOf()
    private val _uiState = MutableStateFlow(
        LogcatUiState(logLevel = LogLevel.entries.getOrNull(DataStore.logLevel) ?: LogLevel.WARN),
    )
    val uiState = _uiState.asStateFlow()

    private var connection: SagerConnection? = null
    private var job: Job? = null

    private fun refilterLogs(logLevel: LogLevel, query: String?): PersistentList<LogItem> {
        return allLogs.filter { item ->
            item.level <= logLevel.ordinal
                    && query?.let { item.message.contains(it, ignoreCase = true) } ?: true
        }.toPersistentList()
    }

    private fun appendLogs(newLogs: List<LogItem>) {
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

    fun initialize(service: ISagerNetService, connection: SagerConnection) {
        clearJob()
        this.connection = connection
        connection.clearLogBuffer()
        job = viewModelScope.launch {
            connection.logLine
                .onStart {
                    service.startLogWatching(true)
                }
                .collect { lines ->
                    appendLogs(lines)
                }
        }
    }

    override fun onCleared() {
        clearJob()
        super.onCleared()
    }

    private fun clearJob() {
        job?.cancel()
        connection?.service?.value?.startLogWatching(false)
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
        connection?.service?.value?.clearLog()
        allLogs = persistentListOf()
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

}
