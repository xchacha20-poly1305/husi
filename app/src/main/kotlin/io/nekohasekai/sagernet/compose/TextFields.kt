package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.readableMessage
import libcore.Libcore

@Composable
fun ValidatedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    singleLine: Boolean = true,
    maxLine: Int = 50,
    validator: (String) -> String? = { null },
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        errorMessage = validator(value.text)
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                errorMessage = validator(it.text)
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions {
                errorMessage = validator(value.text)
                onOk()
            },
            singleLine = singleLine,
            maxLines = maxLine,
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
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    allowMultiLine: Boolean = false,
) {
    val context = LocalContext.current
    fun validate(text: String): String? {
        if (text.isBlank()) {
            return null
        }

        val lines = text.lines()
        if (lines.size > 1 && !allowMultiLine) {
            return "Unexpected new line"
        }

        val errors = linkedSetOf<String>()
        for (link in lines) try {
            val url = Libcore.parseURL(link)
            when (url.scheme.lowercase()) {
                "content" -> continue
                "http" -> errors.add(context.getString(R.string.cleartext_http_warning))
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
    ValidatedTextField(value, onValueChange, onOk, false, validator = ::validate)
}

@Composable
fun DurationTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
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
            Libcore.parseDuration(text)
            null
        } catch (e: Exception) {
            e.readableMessage
        }
    }
    ValidatedTextField(value, onValueChange, onOk, true, validator = ::validate)
}

@Composable
fun UIntegerTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
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

    LaunchedEffect(value.text) {
        errorMessage = validate(value.text)
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                val text = newValue.text
                if (text.isBlank()) {
                    onValueChange(
                        TextFieldValue(
                            text = "0",
                            selection = TextRange(1),
                        ),
                    )
                } else if (text.toUIntOrNull() != null) {
                    onValueChange(
                        if (text.startsWith("0")) {
                            TextFieldValue(
                                text = text.trimStart('0'),
                                selection = TextRange((text.trimStart('0')).length),
                            )
                        } else {
                            newValue
                        },
                    )
                    errorMessage = validate(text)
                }
            },
            modifier = modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions { onOk() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
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
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val text = newValue.text
            // Port: 0-65535, max 5 digits
            if (text.isBlank()) {
                onValueChange(
                    TextFieldValue(
                        text = "0",
                        selection = TextRange(1),
                    ),
                )
            } else {
                val intValue = text.toIntOrNull()
                if (intValue != null && intValue <= 65535) {
                    onValueChange(
                        if (text.startsWith("0")) {
                            TextFieldValue(
                                text = text.trimStart('0'),
                                selection = TextRange(text.trimStart('0').length),
                            )
                        } else {
                            newValue
                        },
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        keyboardActions = KeyboardActions { onOk() },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
fun HostTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        keyboardActions = KeyboardActions { onOk() },
        singleLine = false,
    )
}

@Composable
fun ValidatedIntegerTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
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
        value = value,
        onValueChange = { newValue ->
            val text = newValue.text
            if (text.isBlank() || text.toIntOrNull() != null) {
                onValueChange(newValue)
            }
        },
        onOk = onOk,
        singleLine = true,
        validator = ::validate,
    )
}

@Composable
fun MultilineTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        keyboardActions = KeyboardActions { onOk() },
        singleLine = false,
        maxLines = maxLines,
        label = label,
        placeholder = placeholder,
        visualTransformation = visualTransformation,
        colors = colors,
        interactionSource = interactionSource,
    )
}
