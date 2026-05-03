@file:OptIn(ExperimentalLayoutApi::class)

package fr.husi.compose

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import fr.husi.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.bg.ServiceStatus
import fr.husi.database.DataStore
import fr.husi.ktx.readableUrlTestError
import fr.husi.libcore.Libcore
import fr.husi.resources.*
import fr.husi.ui.MainViewModel
import fr.husi.ui.URLTestStatus
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

@Composable
fun StatsBar(
    modifier: Modifier = Modifier,
    status: ServiceStatus,
    visible: Boolean = true,
    mainViewModel: MainViewModel,
) {
    val urlTestStatus by mainViewModel.urlTestStatus.collectAsStateWithLifecycle()
    val isHTTPS by DataStore.configurationStore
        .stringFlow(Key.CONNECTION_TEST_URL)
        .map { it.startsWith("https://") }
        .collectAsStateWithLifecycle(false)

    var totalHeight by remember { mutableIntStateOf(0) }
    val offsetY by animateIntAsState(
        targetValue = if (visible) 0 else totalHeight,
        label = "statsBarOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { totalHeight = it.height }
            .graphicsLayer { translationY = offsetY.toFloat() }
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = navigationBarsAlwaysInsets()
                    .asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (visible) {
                        Modifier.clickable { mainViewModel.urlTest() }
                    } else {
                        Modifier
                    },
                ),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                    alpha = 0.88f,
                ),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "▲ " + stringResource(
                        Res.string.speed,
                        Libcore.formatBytes(status.speed?.txRateProxy ?: 0L),
                    ),
                )
                Text(
                    text = "▼ " + stringResource(
                        Res.string.speed,
                        Libcore.formatBytes(status.speed?.rxRateProxy ?: 0L),
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                val text = when (urlTestStatus) {
                    URLTestStatus.Initial -> stringResource(Res.string.vpn_connected)
                    URLTestStatus.Testing -> stringResource(Res.string.connection_test_testing)

                    is URLTestStatus.Success -> stringResource(
                        if (isHTTPS) {
                            Res.string.connection_test_available
                        } else {
                            Res.string.connection_test_available_http
                        },
                        (urlTestStatus as URLTestStatus.Success).legacy,
                    )

                    is URLTestStatus.Exception -> {
                        val exception = (urlTestStatus as URLTestStatus.Exception).exception
                        stringResource(
                            Res.string.connection_test_error,
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
}
