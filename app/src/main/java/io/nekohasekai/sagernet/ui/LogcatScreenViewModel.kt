package io.nekohasekai.sagernet.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.utils.LibcoreClientManager
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.LogItem

@Immutable
data class LogcatUiState(
    val pause: Boolean = false,
    val searchQuery: String? = null,
    val logLevel: LogLevel = LogLevel.entries[DataStore.logLevel],
    val logs: PersistentList<LogEntry> = persistentListOf(),
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

@Immutable
data class LogEntry(
    val level: LogLevel,
    val message: String,
)

fun LogItem.toLogEntry(): LogEntry {
    return LogEntry(
        level = LogLevel.entries[level],
        message = message,
    )
}

@Stable
class LogcatScreenViewModel : ViewModel() {

    private var allLogs: PersistentList<LogEntry> = persistentListOf()
    private val _uiState = MutableStateFlow(
        LogcatUiState(logLevel = LogLevel.entries.getOrNull(DataStore.logLevel) ?: LogLevel.WARN),
    )
    val uiState = _uiState.asStateFlow()
    val searchTextFieldState = TextFieldState()

    private var job: Job? = null
    private val clientManager = LibcoreClientManager()
    private var lastLogCount = 0

    init {
        viewModelScope.launch {
            snapshotFlow { searchTextFieldState.text.toString() }
                .drop(1)
                .distinctUntilChanged()
                .collect { setSearchQuery(it.ifEmpty { null }) }
        }
    }

    private fun refilterLogs(logLevel: LogLevel, query: String?): PersistentList<LogEntry> {
        return allLogs.filter { item ->
            item.level.ordinal <= logLevel.ordinal
                    && query?.let { item.message.contains(it, ignoreCase = true) } ?: true
        }.toPersistentList()
    }

    private fun appendLogs(item: LogEntry) {
        allLogs = allLogs.add(item)
        _uiState.update { state ->
            if (state.pause) return
            if (item.level.ordinal > state.logLevel.ordinal) return
            state.copy(logs = state.logs.add(item))
        }
    }

    suspend fun initialize() {
        job?.cancel()
        clientManager.close()
        allLogs = persistentListOf()
        lastLogCount = 0
        _uiState.update { it.copy(logs = persistentListOf()) }

        job = clientManager.subscribeLogs(viewModelScope) { item ->
            appendLogs(item.toLogEntry())
        }
    }

    override fun onCleared() {
        job?.cancel()
        runBlocking {
            clientManager.close()
        }
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

    fun clearLog() = viewModelScope.launch(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                client.clearLog()
            }
        } catch (e: Exception) {
            Logs.w("clear log", e)
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
