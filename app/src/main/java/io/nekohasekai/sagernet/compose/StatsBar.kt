package io.nekohasekai.sagernet.compose

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainViewModel
import kotlinx.coroutines.flow.map

@Composable
fun StatsBar(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    service: ISagerNetService?,
) {
    val context = LocalContext.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isHTTPS by DataStore.configurationStore
        .stringFlow(Key.CONNECTION_TEST_URL)
        .map { it.startsWith("https://") }
        .collectAsStateWithLifecycle(false)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                mainViewModel.urlTest(service)
            },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "▲ " + stringResource(
                    R.string.speed,
                    Formatter.formatFileSize(context, uiState.txSpeed),
                ),
            )
            Text(
                text = "▼ " + stringResource(
                    R.string.speed,
                    Formatter.formatFileSize(context, uiState.rxSpeed),
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            val text = if (uiState.urlTestResult == null) {
                stringResource(R.string.vpn_connected)
            } else stringResource(
                if (isHTTPS) {
                    R.string.connection_test_available
                } else {
                    R.string.connection_test_available_http
                },
                uiState.urlTestResult.toString(),
            )
            Text(text)
        }
    }
}