@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.compose

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import fr.husi.compose.material3.Text

data class DropdownMenuAction(
    val text: String,
    val opensSubmenu: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun DropdownMenuActions(actions: List<DropdownMenuAction>) {
    actions.forEachIndexed { index, action ->
        val shape = MenuDefaults.itemShape(index, actions.size).shape
        if (action.opensSubmenu) {
            ExpandableDropdownMenuItem(
                text = action.text,
                shape = shape,
                onClick = action.onClick,
            )
        } else {
            DropdownMenuItem(
                text = { Text(action.text) },
                onClick = action.onClick,
                shape = shape,
            )
        }
    }
}
