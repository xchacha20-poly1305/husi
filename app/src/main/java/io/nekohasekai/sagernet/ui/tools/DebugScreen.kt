package io.nekohasekai.sagernet.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher

@Composable
internal fun DebugScreen(
    modifier: Modifier = Modifier,
    showSnackbar: (message: String) -> Unit,
) {
    var showResetAlert by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Button(
            onClick = { error("Test crash") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Crash from Kotlin")
        }
        Button(
            onClick = { showResetAlert = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset settings.")
        }
    }
    if (showResetAlert) AlertDialog(
        onDismissRequest = { showResetAlert = false },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                runOnIoDispatcher {
                    DataStore.configurationStore.reset()
                }
                showSnackbar("Cleared")
                showResetAlert = false
            }
        },
        dismissButton = {
            TextButton(stringResource(android.R.string.cancel)) {
                showResetAlert = false
            }
        },
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Dangerous action") },
        text = { Text("Are you sure to reset your settings?") },
    )
}