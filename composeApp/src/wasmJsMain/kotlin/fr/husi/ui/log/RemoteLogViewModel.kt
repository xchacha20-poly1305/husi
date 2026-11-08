package fr.husi.ui.log

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import fr.husi.remote.RemoteState
import fr.husi.remote.text

internal class RemoteLogViewModel(private val remote: RemoteState) : ViewModel() {
    val searchTextFieldState = TextFieldState()
    val levels = listOf("PANIC", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE")
    var level by mutableStateOf("WARN")
    private var paused by mutableStateOf<List<TerminalLogEntry<String>>?>(null)

    private fun liveEntries() =
        remote.logs.map { message ->
            TerminalLogEntry(message.text("level").ifEmpty { "PANIC" }, message.text("message"))
        }

    val presentation: LogcatPresentation<String>
        get() {
            val query = searchTextFieldState.text.toString()
            return LogcatPresentation(
                pause = paused != null,
                searchQuery = query.ifEmpty { null },
                logLevel = level,
                logs =
                    (paused ?: liveEntries()).filter {
                        levels.indexOf(it.level) <= levels.indexOf(level) &&
                            it.message.contains(query, ignoreCase = true)
                    },
                connecting = remote.connecting,
            )
        }

    fun togglePause() {
        paused = if (paused == null) liveEntries() else null
    }

    fun clearSearch() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    fun clear() {
        remote.call("ClearLogs")
        remote.logs.clear()
        paused = paused?.let { emptyList() }
    }

    fun export() = presentation.logs.joinToString("\n") { "[${it.level}] ${it.message}" }
}
