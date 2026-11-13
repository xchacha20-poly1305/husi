package fr.husi.ui.log

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import fr.husi.ktx.emptyAsNull
import fr.husi.remote.RemoteSession
import fr.husi.remote.RemoteState

internal class RemoteLogViewModel(private val remote: RemoteState) : ViewModel() {
    val searchTextFieldState = TextFieldState()
    val levels = listOf("PANIC", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE")
    var level by mutableStateOf("WARN")
    private var paused by mutableStateOf<List<TerminalLogEntry<String>>?>(null)
    private var pausedSession: RemoteSession? = null

    private val currentPause
        get() = paused.takeIf { pausedSession === remote.currentSession }

    private fun liveEntries() =
        remote.logs.map { message ->
            TerminalLogEntry(message.level, message.message)
        }

    val presentation: LogcatPresentation<String>
        get() {
            val query = searchTextFieldState.text.toString()
            return LogcatPresentation(
                pause = currentPause != null,
                searchQuery = query.emptyAsNull(),
                logLevel = level,
                logs = (currentPause ?: liveEntries()).filter {
                    levels.indexOf(it.level) <= levels.indexOf(level)
                        && it.message.contains(query, ignoreCase = true)
                },
                connecting = remote.connecting,
            )
        }

    fun togglePause() {
        paused = if (currentPause == null) liveEntries() else null
        pausedSession = remote.currentSession
    }

    fun clearSearch() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    fun clear() {
        remote.action { clearLogs() }
        remote.logs.clear()
        paused = paused?.let { emptyList() }
    }

    fun export() = presentation.logs.joinToString("\n") { "[${it.level}] ${it.message}" }
}
