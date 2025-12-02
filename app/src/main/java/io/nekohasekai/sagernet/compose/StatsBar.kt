@file:OptIn(ExperimentalLayoutApi::class)

package io.nekohasekai.sagernet.compose

import android.text.format.Formatter
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.ServiceStatus
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.readableUrlTestError
import io.nekohasekai.sagernet.ui.MainViewModel
import io.nekohasekai.sagernet.ui.URLTestStatus
import kotlinx.coroutines.flow.map

@Composable
fun StatsBar(
    modifier: Modifier = Modifier,
    status: ServiceStatus,
    visible: Boolean = true,
    mainViewModel: MainViewModel,
    service: ISagerNetService?,
) {
    val context = LocalContext.current
    val urlTestStatus by mainViewModel.urlTestStatus.collectAsStateWithLifecycle()
    val isHTTPS by DataStore.configurationStore
        .stringFlow(Key.CONNECTION_TEST_URL)
        .map { it.startsWith("https://") }
        .collectAsStateWithLifecycle(false)

    var height by remember { mutableIntStateOf(0) }
    val offsetY by animateIntAsState(
        targetValue = if (visible) 0 else height,
        label = "statsBarOffset",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { height = it.height }
            .graphicsLayer { translationY = offsetY.toFloat() }
            .then(
                if (visible) {
                    Modifier.clickable { mainViewModel.urlTest(service) }
                } else {
                    Modifier
                },
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    bottom = WindowInsets.navigationBarsIgnoringVisibility
                        .asPaddingValues()
                        .calculateBottomPadding(),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "▲ " + stringResource(
                    R.string.speed,
                    Formatter.formatFileSize(context, status.speed?.txRateProxy ?: 0L),
                ),
            )
            Text(
                text = "▼ " + stringResource(
                    R.string.speed,
                    Formatter.formatFileSize(context, status.speed?.rxRateProxy ?: 0L),
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            val text = when (urlTestStatus) {
                URLTestStatus.Initial -> stringResource(R.string.vpn_connected)
                URLTestStatus.Testing -> stringResource(R.string.connection_test_testing)

                is URLTestStatus.Success -> stringResource(
                    if (isHTTPS) {
                        R.string.connection_test_available
                    } else {
                        R.string.connection_test_available_http
                    },
                    (urlTestStatus as URLTestStatus.Success).legacy,
                )

                is URLTestStatus.Exception -> {
                    val exception = (urlTestStatus as URLTestStatus.Exception).exception
                    stringResource(
                        R.string.connection_test_error,
                        readableUrlTestError(exception)?.let {
                            stringResource(it)
                        } ?: exception,
                    )
                }
            }
            Text(text)
        }
    }
}