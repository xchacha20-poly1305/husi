package io.nekohasekai.sagernet.ui.dashboard

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.paddingWithNavigation

@Composable
fun ConnectionDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionDetailViewModel = viewModel(),
    uuid: String,
    connection: SagerConnection,
    popup: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(uuid) {
        viewModel.initialize(connection, uuid)
    }
    val connection by viewModel.connection.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SimpleTopAppBar(
                title = {
                    Text(uuid)
                },
                navigationIcon = ImageVector.vectorResource(R.drawable.arrow_back),
                navigationDescription = stringResource(R.string.back),
                scrollBehavior = scrollBehavior,
                onNavigationClick = popup,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding.paddingWithNavigation(),
        ) {
            item("status", 0) {
                ConnectionDataCard(
                    field = R.string.connection_status,
                    value = {
                        Text(
                            text = if (connection.closed) {
                                stringResource(R.string.connection_status_closed)
                            } else {
                                stringResource(R.string.connection_status_active)
                            },
                            color = if (connection.closed) {
                                Color.Red
                            } else {
                                Color.Green
                            },
                        )
                    },
                )
            }
            item("inbound", 1) {
                ConnectionDataCard(
                    field = R.string.inbound,
                    value = { Text(connection.inbound) },
                )
            }
            if (connection.ipVersion != null) item("ip_version", 1) {
                ConnectionDataCard(
                    field = R.string.ip_version,
                    value = { Text(connection.ipVersion.toString()) },
                )
            }
            item("network", 1) {
                ConnectionDataCard(
                    field = R.string.network,
                    value = { Text(connection.network) },
                )
            }
            item("upload_total", 1) {
                ConnectionDataCard(
                    field = R.string.upload,
                    value = { Text(Formatter.formatFileSize(context, connection.uploadTotal)) },
                )
            }
            item("download_total", 1) {
                ConnectionDataCard(
                    field = R.string.download,
                    value = { Text(Formatter.formatFileSize(context, connection.downloadTotal)) },
                )
            }
            item("start", 1) {
                ConnectionDataCard(
                    field = R.string.start,
                    value = { Text(connection.start) },
                )
            }
            item("source", 1) {
                ConnectionDataCard(
                    field = R.string.source_address,
                    value = { Text(connection.src) },
                )
            }
            item("destination", 1) {
                ConnectionDataCard(
                    field = R.string.destination_address,
                    value = { Text(connection.dst) },
                )
            }
            if (connection.host.isNotBlank()) {
                item("host", 1) {
                    ConnectionDataCard(
                        field = R.string.http_host,
                        value = { Text(connection.host) },
                    )
                }
            }
            item("matched_rule", 1) {
                ConnectionDataCard(
                    field = R.string.outbound_rule,
                    value = { Text(connection.matchedRule) },
                )
            }
            item("outbound", 1) {
                ConnectionDataCard(
                    field = R.string.outbound,
                    value = { Text(connection.outbound) },
                )
            }
            item("chain", 1) {
                ConnectionDataCard(
                    field = R.string.chain,
                    value = { Text(connection.chain) },
                )
            }
            if (connection.protocol != null) item("protocol", 1) {
                ConnectionDataCard(
                    field = R.string.protocol,
                    value = { Text(connection.protocol!!) },
                )
            }
            if (connection.process != null) item("process", 1) {
                ConnectionDataCard(
                    field = R.string.process,
                    value = { Text(connection.process!!) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionDataCard(
    modifier: Modifier = Modifier,
    @StringRes field: Int,
    value: @Composable () -> Unit,
) {
    OutlinedCard(
        onClick = {}, // Make ripple
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(field))
            SelectionContainer {
                value()
            }
        }
    }
}