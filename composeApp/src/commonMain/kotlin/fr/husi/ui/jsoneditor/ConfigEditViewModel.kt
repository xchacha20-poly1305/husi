package fr.husi.ui.jsoneditor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.CoreClient
import fr.husi.core.CoreRpcException
import fr.husi.ktx.Logs
import fr.husi.ktx.kxs
import fr.husi.ktx.readableMessage
import fr.husi.libcore.Libcore
import fr.husi.resources.Res
import fr.husi.resources.ok
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.StringResource
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.milliseconds

@Immutable
sealed interface ConfigEditUiEvent {
    class Finish(val text: String) : ConfigEditUiEvent
    class Alert(val message: String) : ConfigEditUiEvent
    class SnackBar(val id: StringResource) : ConfigEditUiEvent
}

@Immutable
data class ConfigEditUiState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val schemaCompletions: List<ConfigSchemaCompletion> = emptyList(),
    val selectedSchemaCompletion: Int = 0,
)

@Stable
class ConfigEditViewModel(
    initialText: String,
    schema: ConfigSchema = ConfigSchema.CONFIG,
    private val coreClient: CoreClient = GlobalContext.get().get(),
) : ViewModel() {

    val uiEvent: SharedFlow<ConfigEditUiEvent>
        field = MutableSharedFlow<ConfigEditUiEvent>()

    val uiState: StateFlow<ConfigEditUiState>
        field = MutableStateFlow(ConfigEditUiState())

    val textFieldState = TextFieldState("")

    private val historyStack = mutableListOf("")
    private var historyPointer = 0
    private val maxHistorySize = 25

    private var debounceJob: Job? = null
    private val debounceDelay = 500.milliseconds

    private var lastText: String = ""
    private val schemaCompleter = schema.completer

    init {
        initialize(initialText)
    }

    fun initialize(initialText: String) {
        debounceJob?.cancel()
        textFieldState.setTextAndPlaceCursorAtEnd(initialText)
        historyStack.clear()
        historyStack.add(initialText)
        historyPointer = 0
        lastText = initialText
        updateUndoRedoState()
    }

    fun onTextChange(newText: String) {
        if (newText == lastText) return
        lastText = newText

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(debounceDelay)
            addToHistory(newText)
        }
    }

    fun onEditorChange(text: String, selection: TextRange) {
        onTextChange(text)
        uiState.value = uiState.value.copy(
            schemaCompletions = schemaCompleter.complete(text, selection.end),
            selectedSchemaCompletion = 0,
        )
    }

    fun selectSchemaCompletion(offset: Int) {
        val completions = uiState.value.schemaCompletions
        if (completions.isEmpty()) return
        uiState.value = uiState.value.copy(
            selectedSchemaCompletion = (uiState.value.selectedSchemaCompletion + offset).mod(completions.size),
        )
    }

    fun insertText(insertion: String) {
        textFieldState.edit {
            val start = selection.start
            val end = selection.end
            delete(start, end)
            insert(start, insertion)
            selection = TextRange(start + insertion.length)
        }
        addToHistory(textFieldState.text.toString())
    }

    fun applySchemaCompletion(completion: ConfigSchemaCompletion) {
        textFieldState.edit {
            delete(completion.replaceStart, completion.replaceEnd)
            insert(completion.replaceStart, completion.replacement)
            selection = TextRange(completion.replaceStart + completion.cursorOffset)
        }
        val text = textFieldState.text.toString()
        lastText = text
        addToHistory(text)
        onEditorChange(text, textFieldState.selection)
    }

    fun moveCursor(offset: Int) {
        textFieldState.edit {
            val currentPos = selection.start
            val newPos = (currentPos + offset).coerceIn(0, length)
            selection = TextRange(newPos)
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
        uiState.value = uiState.value.copy(
            canUndo = historyPointer > 0,
            canRedo = historyPointer < historyStack.size - 1,
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
                uiEvent.emit(ConfigEditUiEvent.Alert(e.readableMessage))
            }
        }
    }

    fun formatJson(origin: CharSequence?): String {
        if (origin.isNullOrBlank()) {
            return ""
        }
        return Libcore.formatConfig(origin.toString())
    }

    suspend fun checkConfig() {
        checkConfig(textFieldState.text.toString())
    }

    private suspend fun checkConfig(text: String) {
        try {
            val jsonContent = if (text.contains("outbound")) {
                // complete config
                text
            } else {
                // turn single outbound to complete config
                val singleOutbound = kxs.parseToJsonElement(text)
                val jsonArray = JsonArray(listOf(singleOutbound))
                JsonObject(mapOf("outbounds" to jsonArray)).toString()
            }
            // ApplicationService reports parse failures as INVALID_ARGUMENT
            // with the parser message; CoreRpcException.message carries that.
            coreClient.checkConfig(jsonContent)
        } catch (e: CoreRpcException) {
            Logs.i("failed to check config", e)
            uiEvent.emit(ConfigEditUiEvent.Alert(e.message.ifBlank { e.readableMessage }))
            return
        } catch (e: Exception) {
            Logs.i("failed to check config", e)
            uiEvent.emit(ConfigEditUiEvent.Alert(e.readableMessage))
            return
        }
        uiEvent.emit(ConfigEditUiEvent.SnackBar(Res.string.ok))
    }

    fun saveAndExit() = viewModelScope.launch {
        val formatted = try {
            formatJson(textFieldState.text.toString())
        } catch (e: Exception) {
            Logs.w(e)
            uiEvent.emit(ConfigEditUiEvent.Alert(e.readableMessage))
            return@launch
        }
        uiEvent.emit(ConfigEditUiEvent.Finish(formatted))
    }
}
