package fr.husi.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text

@Composable
fun KeyValueLine(
    key: String,
    value: String?,
    modifier: Modifier = Modifier,
    onNullValue: @Composable () -> Unit = { Text("-") },
    color: Color? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )

        if (value == null) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopEnd,
            ) {
                onNullValue()
            }
        } else {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    color = color ?: Color.Unspecified,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
