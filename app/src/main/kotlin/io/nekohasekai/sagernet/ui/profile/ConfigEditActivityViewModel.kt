package io.nekohasekai.sagernet.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import libcore.Libcore

@Immutable
internal sealed interface ConfigEditActivityUiEvent {
    class Finish(val text: String) : ConfigEditActivityUiEvent
    class Alert(val message: String) : ConfigEditActivityUiEvent
    class SnackBar(@param:StringRes val id: Int) : ConfigEditActivityUiEvent
}

@Immutable
internal data class ConfigEditUiState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

@Stable
internal class ConfigEditActivityViewModel : ViewModel() {

    private val _uiEvent = MutableSharedFlow<ConfigEditActivityUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _uiState = MutableStateFlow(ConfigEditUiState())
    val uiState: StateFlow<ConfigEditUiState> = _uiState.asStateFlow()

    val textFieldState = TextFieldState()

    private val historyStack = mutableListOf<String>()
    private var historyPointer = -1
    private val maxHistorySize = 25

    private var debounceJob: Job? = null
    private val debounceDelay = 500L

    private var lastText: String = ""

    fun initialize(initialText: String) {
        textFieldState.setTextAndPlaceCursorAtEnd(initialText)
        lastText = initialText
        addToHistory(initialText)
    }

    fun onTextChange() {
        val newText = textFieldState.text.toString()
        if (newText == lastText) return
        lastText = newText

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(debounceDelay)
            addToHistory(newText)
        }
    }

    fun insertText(insertion: String) {
        textFieldState.edit {
            val start = selection.start
            val end = selection.end
            delete(start, end)
            insert(start, insertion)
            selection = androidx.compose.ui.text.TextRange(start + insertion.length)
        }
        addToHistory(textFieldState.text.toString())
    }

    fun moveCursor(offset: Int) {
        textFieldState.edit {
            val currentPos = selection.start
            val newPos = (currentPos + offset).coerceIn(0, length)
            selection = androidx.compose.ui.text.TextRange(newPos)
        }
    }

    private fun addToHistory(text: String) {
        if (historyPointer >= 0 && historyPointer < historyStack.size && historyStack[historyPointer] == text) {
            return
        }

        if (historyPointer < historyStack.size - 1) {
            historyStack.subList(historyPointer + 1, historyStack.size).clear()
        }

        historyStack.add(text)
        if (historyStack.size > maxHistorySize) {
            historyStack.removeAt(0)
        } else {
            historyPointer++
        }

        updateUndoRedoState()
    }

    fun undo() {
        if (historyPointer > 0) {
            historyPointer--
            updateUndoRedoState()
            val text = historyStack[historyPointer]
            textFieldState.setTextAndPlaceCursorAtEnd(text)
            lastText = text
        }
    }

    fun redo() {
        if (historyPointer < historyStack.size - 1) {
            historyPointer++
            updateUndoRedoState()
            val text = historyStack[historyPointer]
            textFieldState.setTextAndPlaceCursorAtEnd(text)
            lastText = text
        }
    }

    private fun updateUndoRedoState() {
        _uiState.value = _uiState.value.copy(
            canUndo = historyPointer > 0,
            canRedo = historyPointer < historyStack.size - 1
        )
    }

    fun formatCurrentText() {
        try {
            val formatted = formatJson(textFieldState.text)
            textFieldState.setTextAndPlaceCursorAtEnd(formatted)
            lastText = formatted
            addToHistory(formatted)
        } catch (e: Exception) {
            viewModelScope.launch {
                _uiEvent.emit(ConfigEditActivityUiEvent.Alert(e.readableMessage))
            }
        }
    }

    fun formatJson(origin: CharSequence?): String {
        if (origin.isNullOrBlank()) {
            return ""
        }
        return Libcore.formatConfig(origin.toString())
    }

    suspend fun checkConfig(text: String) {
        try {
            val jsonContent = if (text.contains("outbound")) {
                // complete config
                text
            } else {
                // turn single outbound to complete config
                val singleOutbound = JsonParser.parseString(text)
                val jsonArray = JsonArray().also { it.add(singleOutbound) }
                JsonObject().also { it.add("outbounds", jsonArray) }.toString()
            }
            Libcore.checkConfig(jsonContent)
        } catch (e: Exception) {
            Logs.i("failed to check config", e)
            _uiEvent.emit(ConfigEditActivityUiEvent.Alert(e.readableMessage))
            return
        }
        _uiEvent.emit(ConfigEditActivityUiEvent.SnackBar(android.R.string.ok))
    }

    fun saveAndExit(text: String) = viewModelScope.launch {
        val formatted = try {
            formatJson(text)
        } catch (e: Exception) {
            Logs.w(e)
            _uiEvent.emit(ConfigEditActivityUiEvent.Alert(e.readableMessage))
            return@launch
        }
        _uiEvent.emit(ConfigEditActivityUiEvent.Finish(formatted))
    }
}