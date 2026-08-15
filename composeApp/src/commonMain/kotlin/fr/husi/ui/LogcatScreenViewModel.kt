package fr.husi.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.CoreClient
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.libcore.Libcore
import fr.husi.proto.daemon.Log
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Immutable
data class LogcatUiState(
    val pause: Boolean = false,
    val searchQuery: String? = null,
    val logLevel: LogLevel = LogLevel.entries[DataStore.logLevel],
    val logs: PersistentList<LogEntry> = persistentListOf(),
    val errorMessage: String? = null,
    val connecting: Boolean = false,
    val isRemote: Boolean = false,
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

fun Log.Message.toLogEntry(): LogEntry {
    val level = LogLevel.entries.getOrNull(levelValue) ?: LogLevel.INFO
    return LogEntry(
        level = level,
        message = message,
    )
}

@Stable
class LogcatScreenViewModel(
    coreClient: CoreClient? = null,
    private val remoteControl: RemoteControlManager? = null,
) : ViewModel() {
    private val coreClientOverride = coreClient

    private val coreClient: CoreClient
        get() = coreClientOverride
            ?: remoteControl?.activeClient?.value
            ?: GlobalContext.get().get()

    private val isRemote: Boolean
        get() = remoteControl?.isRemote == true

    private var allLogs: PersistentList<LogEntry> = persistentListOf()
    val uiState: StateFlow<LogcatUiState>
        field = MutableStateFlow(
            LogcatUiState(logLevel = LogLevel.entries.getOrNull(DataStore.logLevel) ?: LogLevel.WARN),
        )
    val searchTextFieldState = TextFieldState()

    private var job: Job? = null

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
        allLogs = allLogs.adding(item)
        uiState.update { state ->
            if (state.pause) return
            if (item.level.ordinal > state.logLevel.ordinal) return
            state.copy(logs = state.logs.adding(item))
        }
    }

    suspend fun initialize(isConnected: Boolean) {
        job?.cancel()
        allLogs = persistentListOf()
        uiState.update {
            it.copy(
                logs = persistentListOf(),
                connecting = !isConnected && isRemote,
                isRemote = isRemote,
            )
        }
        if (!isConnected) return

        job = viewModelScope.launch {
            try {
                coreClient.subscribeLog().collect { batch ->
                    if (batch.reset) {
                        allLogs = persistentListOf()
                        uiState.update { state ->
                            if (state.pause) state else state.copy(logs = persistentListOf())
                        }
                    }
                    for (message in batch.messagesList) {
                        appendLogs(message.toLogEntry())
                    }
                }
            } catch (e: Exception) {
                Logs.w("subscribe logs", e)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    fun togglePause() {
        uiState.update { state ->
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
            coreClient.clearLogs()
            if (!isRemote) {
                Libcore.logClear()
            }
        } catch (e: Exception) {
            Logs.w("clear log", e)
        }
        allLogs = persistentListOf()
        uiState.update { it.copy(logs = persistentListOf()) }
    }

    fun setLogLevel(level: LogLevel) {
        uiState.update { state ->
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
        uiState.update { state ->
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
