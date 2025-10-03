package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun <T> DropDownSelector(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    value: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
    displayValue: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = displayValue(value),
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                label = label,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                for (item in values) {
                    DropdownMenuItem(
                        onClick = {
                            onValueChange(item)
                            expanded = false
                        },
                        text = { Text(displayValue(item)) },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun DropDownSelectorStringPreview() {
    val items = listOf("Apple", "Banana", "Cherry", "Date")
    var selectedValue by remember { mutableStateOf(items.first()) }

    DropDownSelector(
        label = { Text("Fruit") },
        value = selectedValue,
        values = items,
        onValueChange = { selectedValue = it },
        displayValue = { it },
    )
}