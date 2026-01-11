package io.nekohasekai.sagernet.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference

object PreferenceType {
    const val CATEGORY = 0
    const val SWITCH = 1
    const val LIST = 2
    const val TEXT_FIELD = 3
    const val MULTI_SELECT_LIST = 4
    const val COLOR_PICKER = 5
}

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
    icon: @Composable (() -> Unit) = {
        Icon(
            ImageVector.vectorResource(R.drawable.password),
            null,
        )
    },
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
                stringResource(id = R.string.not_set)
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
                ),
            )
        },
    )
}

@Composable
fun <K, V> MapPreference(
    modifier: Modifier = Modifier,
    value: LinkedHashMap<K, V>,
    keys: LinkedHashSet<K>,
    onValueChange: (LinkedHashMap<K, V>) -> Unit,
    displayKey: (K) -> String = { it.toString() },
    valueToText: (V) -> String = { it.toString() },
    textToValue: (String) -> V,
    enabled: Boolean = true,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
) {
    var openDialog by remember { mutableStateOf(false) }

    Preference(
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        onClick = { openDialog = true },
    )

    if (!openDialog) return

    val firstKey = remember(keys) { keys.firstOrNull() }
    val focusRequester = remember { FocusRequester() }
    val textStates = remember(keys, value) {
        mutableStateMapOf<K, TextFieldValue>().apply {
            for (key in keys) {
                val text = value[key]?.let(valueToText).orEmpty()
                this[key] = TextFieldValue(text, TextRange(text.length))
            }
        }
    }
    val onOk = {
        val newMap = LinkedHashMap<K, V>()
        for (key in keys) {
            val text = textStates[key]!!.text
            newMap[key] = textToValue(text)
        }
        onValueChange(newMap)
        openDialog = false
    }

    AlertDialog(
        onDismissRequest = { openDialog = false },
        title = { title() },
        text = {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scroll),
            ) {
                var isFirst = true
                for (key in keys) {
                    val textFieldValue = textStates.getOrPut(key) {
                        val text = value[key]?.let(valueToText).orEmpty()
                        TextFieldValue(text, TextRange(text.length))
                    }
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textStates[key] = it },
                        label = { Text(displayKey(key)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isFirst) 0.dp else 12.dp)
                            .then(if (key == firstKey) Modifier.focusRequester(focusRequester) else Modifier),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onOk() }),
                    )
                    isFirst = false
                }
            }
        },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok), onClick = onOk)
        },
        dismissButton = {
            TextButton(stringResource(android.R.string.cancel)) {
                openDialog = false
            }
        },
    )

    LaunchedEffect(focusRequester) {
        if (firstKey != null) focusRequester.requestFocus()
    }
}

@SuppressLint("MutableCollectionMutableState")
@Preview
@Composable
private fun PreviewCustomPreference() {
    ProvidePreferenceLocals {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceCategory(
                icon = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.settings),
                        contentDescription = null,
                    )
                },
                text = { Text("Account Settings") },
            )

            Spacer(Modifier.height(16.dp))

            var password by remember { mutableStateOf("") }
            PasswordPreference(
                value = password,
                onValueChange = { password = it },
                enabled = true,
            )

            Spacer(Modifier.height(16.dp))

            var mapValue by remember {
                mutableStateOf(
                    linkedMapOf(
                        "Server" to "192.168.1.1",
                        "Port" to "8080",
                    ),
                )
            }

            val keys = linkedSetOf("Server", "Port")

            MapPreference(
                value = mapValue,
                keys = keys,
                onValueChange = { mapValue = it },
                displayKey = { it },
                textToValue = { it },
                title = { Text("Connection Settings") },
                icon = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.wifi),
                        contentDescription = null,
                    )
                },
                summary = {
                    Text(mapValue.entries.joinToString { "${it.key}: ${it.value}" })
                },
            )
        }
    }
}
