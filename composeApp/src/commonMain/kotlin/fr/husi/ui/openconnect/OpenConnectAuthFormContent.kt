package fr.husi.ui.openconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.husi.compose.DropDownSelector
import fr.husi.compose.material3.Text
import fr.husi.vpn.OpenConnectAuthChoice
import fr.husi.vpn.OpenConnectAuthField
import fr.husi.vpn.OpenConnectAuthFormState

private const val FIELD_KIND_SELECT = "select"
private const val FIELD_KIND_PASSWORD = "password"

fun initialAuthFormValues(form: OpenConnectAuthFormState): Map<String, String> {
    return form.fields.associate { field ->
        field.submissionKey to initialFieldValue(field)
    }
}

private fun initialFieldValue(field: OpenConnectAuthField): String {
    if (field.kind != FIELD_KIND_SELECT) return field.value
    if (field.options.any { it.value == field.value }) return field.value
    return field.options.firstOrNull()?.value.orEmpty()
}

@Composable
fun OpenConnectAuthFormContent(
    form: OpenConnectAuthFormState,
    values: SnapshotStateMap<String, String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (field in form.fields) {
            AuthFieldInput(
                field = field,
                value = values[field.submissionKey].orEmpty(),
                onValueChange = { values[field.submissionKey] = it },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun AuthFieldInput(
    field: OpenConnectAuthField,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val label = field.label.ifEmpty { field.name }
    when (field.kind) {
        FIELD_KIND_SELECT -> {
            val selected = remember(field, value) {
                field.options.firstOrNull { it.value == value }
                    ?: OpenConnectAuthChoice(value = value, label = value)
            }
            DropDownSelector(
                label = { Text(label) },
                value = selected,
                values = field.options,
                onValueChange = { onValueChange(it.value) },
                displayValue = { it.label.ifEmpty { it.value } },
            )
        }

        FIELD_KIND_PASSWORD -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        else -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
