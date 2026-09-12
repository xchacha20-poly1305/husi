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
import fr.husi.repository.resolveRepository
import fr.husi.utils.LogExport
import fr.husi.utils.RemoteLogTarget
import fr.husi.utils.SendLog
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

private const val MAX_LOG_ENTRIES = 3000

private const val LOG_TRIM_SLACK = 500

private const val UI_STATE_STOP_TIMEOUT = 5000L

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

@Immutable
private data class LogcatFilter(
    val logLevel: LogLevel,
    val searchQuery: String? = null,
) {
    fun accepts(entry: LogEntry): Boolean {
        if (entry.level.number > logLevel.number) return false
        return searchQuery == null || entry.message.contains(searchQuery, ignoreCase = true)
    }
}

@Immutable
private data class LogcatStatus(
    val errorMessage: String? = null,
    val connecting: Boolean = false,
    val isRemote: Boolean = false,
)

private fun PersistentList<LogEntry>.appendBounded(
    entries: List<LogEntry>,
): PersistentList<LogEntry> {
    val appended = addingAll(entries)
    if (appended.size <= MAX_LOG_ENTRIES + LOG_TRIM_SLACK) return appended
    return appended.subList(appended.size - MAX_LOG_ENTRIES, appended.size).toPersistentList()
}

private fun buildUiState(
    liveLogs: PersistentList<LogEntry>,
    pausedLogs: PersistentList<LogEntry>?,
    filter: LogcatFilter,
    status: LogcatStatus,
): LogcatUiState {
    val displayed = pausedLogs ?: liveLogs
    return LogcatUiState(
        pause = pausedLogs != null,
        searchQuery = filter.searchQuery,
        logLevel = filter.logLevel,
        logs = displayed.filter(filter::accepts).toPersistentList(),
        errorMessage = status.errorMessage,
        connecting = status.connecting,
        isRemote = status.isRemote,
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

    private val liveLogs = MutableStateFlow(persistentListOf<LogEntry>())

    private val pausedLogs = MutableStateFlow<PersistentList<LogEntry>?>(null)

    private val filter = MutableStateFlow(LogcatFilter(logLevel = localLogLevel))
    private val status = MutableStateFlow(LogcatStatus())

    val uiState: StateFlow<LogcatUiState> = combine(
        liveLogs,
        pausedLogs,
        filter,
        status,
        ::buildUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT),
        initialValue = buildUiState(
            liveLogs = liveLogs.value,
            pausedLogs = pausedLogs.value,
            filter = filter.value,
            status = status.value,
        ),
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

    suspend fun buildExportLog(): LogExport {
        val session = remoteControl?.session?.value
            ?: return withContext(Dispatchers.IO) {
                SendLog.buildLocalLog(resolveRepository().externalAssetsDir)
            }
        val logLines = liveLogs.value.map { it.message }
        return SendLog.buildRemoteLog(
            target = RemoteLogTarget(
                name = session.server.name,
                url = session.server.url,
                version = fetchRemoteVersion(),
                logLevel = fetchRemoteLogLevel().name,
            ),
            logLines = logLines,
        )
    }

    private suspend fun fetchRemoteVersion(): String {
        return try {
            val version = coreClient.getVersion()
            "husi ${version.version}, sing-box ${version.singBoxVersion}, ${version.buildEnvironment}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("get remote version", e)
            "unknown"
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
        liveLogs.value = persistentListOf()
        pausedLogs.value = null
        val remote = isRemote
        filter.update { it.copy(logLevel = localLogLevel) }
        status.value = LogcatStatus(
            connecting = !isConnected && remote,
            isRemote = remote,
        )
        if (!isConnected) return

        job = viewModelScope.launch {
            if (remote) {
                val level = fetchRemoteLogLevel()
                filter.update { it.copy(logLevel = level) }
            }
            try {
                coreClient.subscribeLog().collect { batch ->
                    if (batch.reset) clearLogBuffers()
                    val entries = batch.messagesList.map { it.toLogEntry() }
                    liveLogs.update { it.appendBounded(entries) }
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

    private fun clearLogBuffers() {
        liveLogs.value = persistentListOf()
        pausedLogs.update { snapshot -> snapshot?.let { persistentListOf() } }
    }

    fun togglePause() {
        pausedLogs.update { snapshot -> if (snapshot == null) liveLogs.value else null }
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
        clearLogBuffers()
    }

    fun setLogLevel(level: LogLevel) {
        filter.update { it.copy(logLevel = level) }
    }

    fun setSearchQuery(query: String?) {
        filter.update { it.copy(searchQuery = query) }
    }

    fun clearSearchQuery() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

}
