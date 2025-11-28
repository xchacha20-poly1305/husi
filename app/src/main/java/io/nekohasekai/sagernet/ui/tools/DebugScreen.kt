package io.nekohasekai.sagernet.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher

@Composable
internal fun DebugScreen(
    modifier: Modifier = Modifier,
    onVisibleChange: (Boolean) -> Unit,
    showSnackbar: (message: String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val visible by rememberScrollHideState(scrollState)

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    var showResetAlert by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "Debug Actions",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { error("Test crash") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crash from Kotlin")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showResetAlert = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset settings")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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