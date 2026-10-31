package fr.husi.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.ktx.parseGoDuration
import fr.husi.ktx.readableMessage
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.cleartext_http_warning
import io.github.xchacha20_poly1305.kpuri.Url

@Composable
fun ValidatedTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    singleLine: Boolean = true,
    maxLine: Int = 50,
    modifier: Modifier = Modifier,
    inputTransformation: InputTransformation? = null,
    validator: suspend (String) -> String? = { null },
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.text.toString()) {
        errorMessage = validator(state.text.toString())
    }

    Column {
        OutlinedTextField(
            state = state,
            inputTransformation = inputTransformation,
            modifier = modifier.fillMaxWidth(),
            onKeyboardAction = { onOk() },
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine
            else TextFieldLineLimits.MultiLine(maxHeightInLines = maxLine),
            isError = errorMessage != null,
            supportingText = {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            },
        )
    }
}

@Composable
fun LinkOrContentTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    allowMultiLine: Boolean = false,
) {
    suspend fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }

        val lines = text.lines()
        if (lines.size > 1 && !allowMultiLine) {
            return "Unexpected new line"
        }

        val errors = linkedSetOf<String>()
        for (link in lines) try {
            val url = Url.parse(link)
            when (url.scheme) {
                "content" -> continue
                "http" -> errors.add(resolveRepository().getString(Res.string.cleartext_http_warning))
            }
        } catch (e: Exception) {
            errors.add(e.readableMessage)
        }

        return if (errors.isNotEmpty()) {
            errors.joinToString("\n")
        } else {
            null
        }
    }
    ValidatedTextField(state, onOk, false, validator = ::validate)
}

@Composable
fun DurationTextField(
    state: TextFieldState,
    onOk: () -> Unit,
) {
    fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }

        if (text.lines().size > 1) {
            return "Unexpected new line"
        }

        return try {
            parseGoDuration(text)
            null
        } catch (e: Exception) {
            e.readableMessage
        }
    }
    ValidatedTextField(state, onOk, true, validator = ::validate)
}

@Composable
fun UIntegerTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int? = null,
    maxValue: Int? = null,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }
        val intValue = text.toIntOrNull() ?: return null // Will be handled by input filter
        if (minValue != null && intValue < minValue) {
            return "Must be ≥ $minValue"
        }
        if (maxValue != null && intValue > maxValue) {
            return "Must be ≤ $maxValue"
        }
        return null
    }

    LaunchedEffect(state.text.toString()) {
        errorMessage = validate(state.text.toString())
    }

    Column {
        OutlinedTextField(
            state = state,
            inputTransformation = unsignedIntegerInputTransformation(),
            modifier = modifier.fillMaxWidth(),
            onKeyboardAction = { onOk() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = errorMessage != null,
            supportingText = errorMessage?.let {
                {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            },
        )
    }
}

@Composable
fun PortTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        inputTransformation = unsignedIntegerInputTransformation(maxValue = 65535u),
        modifier = modifier.fillMaxWidth(),
        onKeyboardAction = { onOk() },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

@Composable
fun HostTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    OutlinedTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        onKeyboardAction = { onOk() },
        lineLimits = TextFieldLineLimits.MultiLine(),
    )
}

@Composable
fun ValidatedIntegerTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    minValue: Int? = null,
    maxValue: Int? = null,
    modifier: Modifier = Modifier,
) {
    fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }
        val intValue = text.toIntOrNull() ?: return "Invalid number"
        if (minValue != null && intValue < minValue) {
            return "Must be >= $minValue"
        }
        if (maxValue != null && intValue > maxValue) {
            return "Must be <= $maxValue"
        }
        return null
    }

    ValidatedTextField(
        state = state,
        inputTransformation = InputTransformation {
            val text = asCharSequence().toString()
            if (text.isNotBlank() && text.toIntOrNull() == null) revertAllChanges()
        },
        onOk = onOk,
        modifier = modifier,
        singleLine = true,
        validator = ::validate,
    )
}

@Composable
fun SingleLineTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        onKeyboardAction = { onOk() },
        lineLimits = TextFieldLineLimits.SingleLine,
        supportingText = supportingText,
    )
}

@Composable
fun MultilineTextField(
    state: TextFieldState,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        onKeyboardAction = { onOk() },
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = maxLines),
        label = label?.let { content -> { content() } },
        placeholder = placeholder,
        colors = colors,
        interactionSource = interactionSource,
    )
}

internal fun unsignedIntegerInputTransformation(maxValue: UInt = UInt.MAX_VALUE): InputTransformation =
    InputTransformation {
        val text = asCharSequence().toString()
        if (text.isBlank()) {
            replace(0, length, "0")
        } else {
            val number = text.toUIntOrNull()
            if (number == null || number > maxValue) {
                revertAllChanges()
            } else {
                // Keep one zero when the entire input consists of zeros.
                val leadingZeros = text.indexOfFirst { it != '0' }.let {
                    if (it < 0) {
                        text.length - 1
                    } else {
                        it
                    }
                }
                if (leadingZeros > 0) {
                    replace(0, leadingZeros, "")
                }
            }
        }
    }
