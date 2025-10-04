package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
            }
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val text = newValue.text
            if (text.isBlank()) {
                onValueChange(TextFieldValue("0"))
            } else if (text.toUIntOrNull() != null) {
                onValueChange(newValue)
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