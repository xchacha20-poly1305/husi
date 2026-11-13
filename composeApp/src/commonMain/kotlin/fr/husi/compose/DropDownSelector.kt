package fr.husi.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import fr.husi.compose.material3.Text

/**
 * @param textToValue Supplying it enables typing and converts each edit into a value for [onValueChange].
 */
@Composable
fun <T> DropDownSelector(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    value: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
    displayValue: (T) -> String = { it.toString() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    textToValue: ((String) -> T)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val editable = textToValue != null

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayValue(value),
            onValueChange = { text ->
                textToValue?.let { parse -> onValueChange(parse(text)) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    if (editable) {
                        ExposedDropdownMenuAnchorType.PrimaryEditable
                    } else {
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable
                    },
                ),
            readOnly = !editable,
            singleLine = true,
            shape = shape,
            label = label,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = if (editable) {
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)
                    } else {
                        Modifier
                    },
                )
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
