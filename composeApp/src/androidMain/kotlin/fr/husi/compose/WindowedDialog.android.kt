package fr.husi.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun WindowedDialog(
    onDismissRequest: () -> Unit,
    title: String,
    windowSize: DpSize,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val windowHeight = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val maxHeight = windowHeight.takeIf { it > 0.dp }?.times(0.8f) ?: 560.dp

    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column {
                Text(
                    text = title,
                    modifier = Modifier.padding(
                        start = 24.dp,
                        top = 24.dp,
                        end = 24.dp,
                        bottom = 8.dp,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(Modifier.weight(1f, fill = false)) {
                    content()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 24.dp,
                            top = 8.dp,
                            end = 24.dp,
                            bottom = 24.dp,
                        ),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    buttons()
                }
            }
        }
    }
}
