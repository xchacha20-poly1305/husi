package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.PreferenceCategory

/**
 * Not only support icon, but also use spacer as icon if not set.
 * */
@Composable
fun PreferenceCategory(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { Spacer(Modifier.size(24.dp)) },
    text: @Composable () -> Unit,
) {
    PreferenceCategory(
        title = {
            Row {
                icon()
                Spacer(Modifier.padding(8.dp))
                text()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun PasswordPreference(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    title: @Composable () -> Unit = { Text(stringResource(R.string.password)) },
    enabled: Boolean = true,
    icon: @Composable (() -> Unit) = { Icon(Icons.Filled.Password, null) },
) {
    TextFieldPreference(
        value = value,
        onValueChange = onValueChange,
        title = title,
        textToValue = { it },
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = {
            val summaryText = if (value.isEmpty()) {
                stringResource(id = androidx.preference.R.string.not_set)
            } else {
                "\u2022".repeat(value.length)
            }
            Text(text = summaryText)
        },
        valueToText = { it },
        textField = { textFieldValue, onTextFieldValueChange, onOk ->
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = onTextFieldValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onOk() },
                )
            )
        }
    )
}