@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun <T> DropDownSelector(
    modifier: Modifier = Modifier,
    label: @Composable TextFieldLabelScope.() -> Unit,
    value: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
    displayValue: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    val textFieldState = rememberTextFieldState()
    LaunchedEffect(value) {
        textFieldState.setTextAndPlaceCursorAtEnd(displayValue(value))
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            state = textFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = label,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MenuDefaults.groupStandardContainerColor,
            shape = MenuDefaults.standaloneGroupShape,
        ) {
            for ((i, item) in values.withIndex()) {
                val isSelected = item == value
                DropdownMenuItem(
                    selected = isSelected,
                    text = {
                        Text(displayValue(item))
                    },
                    onClick = {
                        onValueChange(item)
                        expanded = false
                    },
                    shapes = MenuDefaults.itemShape(i, values.size),
                    colors = MenuDefaults.selectableItemColors(),
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
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