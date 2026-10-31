package fr.husi.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import fr.husi.compose.material3.Text

@Composable
fun <T> AutoCompleteTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    suggestions: List<T>,
    onChooseSuggestion: (T) -> Unit,
    modifier: Modifier = Modifier,
    displaySuggestion: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
) {
    // Suggestions are for typing, so an untouched field starts without them, even if its initial
    // value already has something to suggest.
    var dismissedText by remember(state) { mutableStateOf(state.text.toString()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val text = state.text.toString()
    val expanded = enabled && text != dismissedText && suggestions.isNotEmpty()
    LaunchedEffect(text) {
        selectedIndex = 0
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expand ->
            // Typing is what opens the menu, so only closing is honored here.
            if (!expand) {
                dismissedText = state.text.toString()
            }
        },
        modifier = modifier,
    ) {
        MultilineTextField(
            state = state,
            onOk = onOk,
            modifier = Modifier
                // Hijack key event before menu anchor to prevent the original logic,
                // whose tab means focusing on next.
                .onPreviewKeyEvent { keyEvent ->
                    if (!expanded) {
                        return@onPreviewKeyEvent false
                    }
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                selectedIndex =
                                    (selectedIndex + 1).fastCoerceAtMost(suggestions.lastIndex)
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                selectedIndex = (selectedIndex - 1).fastCoerceAtLeast(-1)
                            }
                            true
                        }

                        Key.Tab -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val index = selectedIndex.takeIf { it in suggestions.indices } ?: 0
                                onChooseSuggestion(suggestions[index])
                                dismissedText = state.text.toString()
                            }
                            true
                        }

                        else -> false
                    }
                }
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled)
                .fillMaxWidth(),
            enabled = enabled,
            label = label,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { dismissedText = state.text.toString() },
            containerColor = MenuDefaults.groupStandardContainerColor,
            shape = MenuDefaults.standaloneGroupShape,
        ) {
            AutoCompleteSuggestionList(
                suggestions = suggestions,
                selectedIndex = selectedIndex,
                onChooseSuggestion = { suggestion ->
                    onChooseSuggestion(suggestion)
                    dismissedText = state.text.toString()
                },
                itemContent = { Text(displaySuggestion(it)) },
            )
        }
    }
}
