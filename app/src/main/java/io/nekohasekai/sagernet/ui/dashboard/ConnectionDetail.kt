package io.nekohasekai.sagernet.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.withNavigation
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.RouteSettingsActivityUiState

private enum class ConnectionFields {
    // STATUS,
    // INBOUND,
    // IP_VERSION,
    NETWORK,

    // UPLOAD_TOTAL,
    // DOWNLOAD_TOTAL,
    // START,
    SOURCE,
    DESTINATION,
    HOST,

    // MATCHED_RULE,
    // OUTBOUND,
    // CHAIN,
    PROTOCOL,
    PROCESS,
}

@Composable
fun ConnectionDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionDetailViewModel = viewModel(),
    uuid: String,
    connection: SagerConnection,
    popup: () -> Unit,
    navigateToRoutes: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(uuid) {
        viewModel.initialize(connection, uuid)
    }
    val routeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            navigateToRoutes()
        }
    }
    var isSelecting by remember { mutableStateOf(false) }
    var selectedField by remember { mutableStateOf(emptySet<ConnectionFields>()) }
    val connection by viewModel.connection.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(uuid)
                },
                navigationIcon = {
                    if (isSelecting) {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.close),
                            contentDescription = stringResource(android.R.string.cancel),
                            onClick = {
                                selectedField = emptySet()
                                isSelecting = false
                            },
                        )
                    } else {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back),
                            onClick = popup,
                        )
                    }
                },
                actions = {
                    AppBarRow {
                        if (isSelecting) {
                            val textOK = context.getString(android.R.string.ok)
                            clickableItem(
                                onClick = {
                                    routeSettingsLauncher.launch(
                                        createIntent(context, selectedField, connection),
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.done),
                                        contentDescription = textOK,
                                    )
                                },
                                label = textOK,
                            )
                        } else {
                            val textCreateRule = context.getString(R.string.create_rule)
                            clickableItem(
                                onClick = { isSelecting = true },
                                icon = {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.add_road),
                                        contentDescription = textCreateRule,
                                    )
                                },
                                label = textCreateRule,
                            )
                            val textClose = context.getString(R.string.close)
                            clickableItem(
                                onClick = {
                                    viewModel.closeConnection(uuid)
                                    popup()
                                },
                                icon = {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.delete_forever),
                                        contentDescription = textClose,
                                    )
                                },
                                label = textClose,
                            )
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding.withNavigation(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    isSelecting = isSelecting,
                    isSelected = ConnectionFields.NETWORK in selectedField,
                    onSelectedChange = { checked ->
                        selectedField = selectedField.toMutableSet().apply {
                            if (checked) {
                                add(ConnectionFields.NETWORK)
                            } else {
                                remove(ConnectionFields.NETWORK)
                            }
                        }
                    },
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
                    isSelecting = isSelecting,
                    isSelected = ConnectionFields.SOURCE in selectedField,
                    onSelectedChange = { checked ->
                        selectedField = selectedField.toMutableSet().apply {
                            if (checked) {
                                add(ConnectionFields.SOURCE)
                            } else {
                                remove(ConnectionFields.SOURCE)
                            }
                        }
                    },
                )
            }
            item("destination", 1) {
                ConnectionDataCard(
                    field = R.string.destination_address,
                    value = { Text(connection.dst) },
                    isSelecting = isSelecting,
                    isSelected = ConnectionFields.DESTINATION in selectedField,
                    onSelectedChange = { checked ->
                        selectedField = selectedField.toMutableSet().apply {
                            if (checked) {
                                add(ConnectionFields.DESTINATION)
                            } else {
                                remove(ConnectionFields.DESTINATION)
                            }
                        }
                    },
                )
            }
            if (connection.host.isNotBlank()) {
                item("host", 1) {
                    ConnectionDataCard(
                        field = R.string.http_host,
                        value = { Text(connection.host) },
                        isSelecting = isSelecting,
                        isSelected = ConnectionFields.HOST in selectedField,
                        onSelectedChange = { checked ->
                            selectedField = selectedField.toMutableSet().apply {
                                if (checked) {
                                    add(ConnectionFields.HOST)
                                } else {
                                    remove(ConnectionFields.HOST)
                                }
                            }
                        },
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
                    isSelecting = isSelecting,
                    isSelected = ConnectionFields.PROTOCOL in selectedField,
                    onSelectedChange = { checked ->
                        selectedField = selectedField.toMutableSet().apply {
                            if (checked) {
                                add(ConnectionFields.PROTOCOL)
                            } else {
                                remove(ConnectionFields.PROTOCOL)
                            }
                        }
                    },
                )
            }
            if (connection.process != null) item("process", 1) {
                ConnectionDataCard(
                    field = R.string.process,
                    value = {
                        val text = "[${connection.uid}] ${connection.process}"
                        Text(text)
                    },
                    isSelecting = isSelecting,
                    isSelected = ConnectionFields.PROCESS in selectedField,
                    onSelectedChange = { checked ->
                        selectedField = selectedField.toMutableSet().apply {
                            if (checked) {
                                add(ConnectionFields.PROCESS)
                            } else {
                                remove(ConnectionFields.PROCESS)
                            }
                        }
                    },
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
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(80),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rotation",
    )

    OutlinedCard(
        onClick = {
            if (isSelecting) onSelectedChange(!isSelected)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                rotationZ = if (isSelecting) rotation else 0f
            },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(field),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer {
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                        value()
                    }
                }
            }
        }
    }
}

private fun createIntent(
    context: Context,
    fields: Set<ConnectionFields>,
    connection: Connection,
): Intent {
    var domains = ""
    var ip = ""
    var port = ""
    var source = ""
    var sourcePort = ""
    var network = emptySet<String>()
    var protocol = emptySet<String>()
    var packages = emptySet<String>()

    for (field in fields) {
        when (field) {
            ConnectionFields.HOST -> {
                if (connection.host.isNotBlank()) {
                    domains = "full:${connection.host}"
                }
            }

            ConnectionFields.DESTINATION -> {
                val (dstIp, dstPort) = parseAddress(connection.dst)
                if (dstIp.isNotBlank()) ip = dstIp
                if (dstPort.isNotBlank()) port = dstPort
            }

            ConnectionFields.SOURCE -> {
                val (srcIp, srcPort) = parseAddress(connection.src)
                if (srcIp.isNotBlank()) source = srcIp
                if (srcPort.isNotBlank()) sourcePort = srcPort
            }

            ConnectionFields.NETWORK -> {
                if (connection.network.isNotBlank()) {
                    network = setOf(connection.network)
                }
            }

            ConnectionFields.PROTOCOL -> {
                if (connection.protocol != null) {
                    protocol = setOf(connection.protocol)
                }
            }

            ConnectionFields.PROCESS -> {
                if (connection.process != null) {
                    packages = setOf(connection.process)
                }
            }

        }
    }

    return Intent(context, RouteSettingsActivity::class.java)
        .putExtra(
            RouteSettingsActivity.EXTRA_ROUTE,
            RouteSettingsActivityUiState(
                name = connection.uuid,
                action = SingBoxOptions.ACTION_ROUTE,
                domains = domains,
                ip = ip,
                port = port,
                source = source,
                sourcePort = sourcePort,
                network = network,
                protocol = protocol,
                packages = packages,
            ),
        )
}

private fun parseAddress(address: String): Pair<String, String> {
    if (address.isBlank()) return "" to ""
    return if (address.startsWith("[")) {
        // IPv6
        val closeBracket = address.indexOf(']')
        if (closeBracket == -1) return address to ""
        val ip = address.substring(1, closeBracket)
        val port = if (closeBracket + 2 < address.length) {
            address.substring(closeBracket + 2)
        } else ""
        ip to port
    } else {
        // IPv4
        val lastColon = address.lastIndexOf(':')
        if (lastColon == -1) return address to ""
        address.take(lastColon) to address.substring(lastColon + 1)
    }
}