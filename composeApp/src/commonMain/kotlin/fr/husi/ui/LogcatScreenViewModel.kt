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
import fr.husi.proto.daemon.LogLevel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
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
    val logLevel: LogLevel = LogLevel.WARN,
    val logs: PersistentList<LogEntry> = persistentListOf(),
    val errorMessage: String? = null,
    val connecting: Boolean = false,
    val isRemote: Boolean = false,
)

val logLevels: List<LogLevel> = LogLevel.entries - LogLevel.UNRECOGNIZED

@Immutable
data class LogEntry(
    val level: LogLevel,
    val message: String,
)

fun Log.Message.toLogEntry(): LogEntry {
    val level = LogLevel.forNumber(levelValue) ?: LogLevel.INFO
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

    private val localLogLevel: LogLevel
        get() = LogLevel.forNumber(DataStore.logLevel.getBlocking()) ?: LogLevel.WARN

    private var allLogs: PersistentList<LogEntry> = persistentListOf()
    val uiState: StateFlow<LogcatUiState>
        field = MutableStateFlow(LogcatUiState(logLevel = localLogLevel))
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
            item.level.number <= logLevel.number
                    && query?.let { item.message.contains(it, ignoreCase = true) } ?: true
        }.toPersistentList()
    }

    private fun appendLogs(item: LogEntry) {
        allLogs = allLogs.adding(item)
        uiState.update { state ->
            if (state.pause) return
            if (item.level.number > state.logLevel.number) return
            state.copy(logs = state.logs.adding(item))
        }
    }

    private suspend fun fetchRemoteLogLevel(): LogLevel {
        return try {
            LogLevel.forNumber(coreClient.getDefaultLogLevel().levelValue) ?: LogLevel.WARN
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("get remote log level", e)
            LogLevel.WARN
        }
    }

    suspend fun initialize(isConnected: Boolean) {
        job?.cancel()
        allLogs = persistentListOf()
        val remote = isRemote
        uiState.update {
            it.copy(
                logs = persistentListOf(),
                logLevel = localLogLevel,
                connecting = !isConnected && remote,
                isRemote = remote,
            )
        }
        if (!isConnected) return

        job = viewModelScope.launch {
            if (remote) {
                val level = fetchRemoteLogLevel()
                uiState.update { it.copy(logLevel = level) }
            }
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
