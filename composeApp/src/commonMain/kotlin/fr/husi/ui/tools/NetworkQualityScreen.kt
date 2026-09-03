@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.core.NetworkQualityPhase
import fr.husi.core.remote.RemoteControlManager
import fr.husi.libcore.Libcore
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.cancel
import fr.husi.resources.download_capacity
import fr.husi.resources.download_responsiveness
import fr.husi.resources.elapsed_time
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.idle_latency
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.network_quality_config_url
import fr.husi.resources.network_quality_http3
import fr.husi.resources.network_quality_max_runtime
import fr.husi.resources.network_quality_serial
import fr.husi.resources.network_quality_test
import fr.husi.resources.ok
import fr.husi.resources.responsiveness_rpm
import fr.husi.resources.speed
import fr.husi.resources.start
import fr.husi.resources.upload_capacity
import fr.husi.resources.upload_responsiveness
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.PreviewContainer
import fr.husi.ui.getStringOrRes
import fr.husi.ui.remote.RemoteTargetMenuSection
import fr.husi.ui.stringOrRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

@Composable
internal fun NetworkQualityScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    onOpenRemoteControl: () -> Unit,
    remoteControl: RemoteControlManager = koinInject(),
    viewModel: NetworkQualityScreenViewModel = viewModel {
        NetworkQualityScreenViewModel(remoteControl = remoteControl)
    },
) {
    val snackbar = LocalSnackbarEmitter.current
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val targetConnected by remoteControl.targetConnected.collectAsStateWithLifecycle()
    var expandMenu by remember { mutableStateOf(false) }

    var alert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is NetworkQualityScreenUiEvent.Snackbar -> snackbar.show(event.message)

                is NetworkQualityScreenUiEvent.ErrorAlert -> {
                    alert = getStringOrRes(event.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                title = { Text(stringResource(Res.string.network_quality_test)) },
                actions = {
                    CapsuleActionButton {
                        Box {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.more_vert),
                                contentDescription = stringResource(Res.string.more),
                                onClick = { expandMenu = true },
                            )
                            DropdownMenuPopup(
                                expanded = expandMenu,
                                onDismissRequest = { expandMenu = false },
                            ) {
                                RemoteTargetMenuSection(
                                    groupIndex = 0,
                                    groupCount = 1,
                                    onManage = onOpenRemoteControl,
                                    onDismiss = { expandMenu = false },
                                )
                            }
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value = uiState.configUrl,
                        onValueChange = { viewModel.setConfigUrl(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        label = { Text(stringResource(Res.string.network_quality_config_url)) },
                        singleLine = false,
                        isError = uiState.urlError != null,
                        supportingText = uiState.urlError?.let { { ErrorText(stringOrRes(it)) } },
                    )
                    Spacer(Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(
                        value = uiState.maxRuntimeSeconds.toString(),
                        onValueChange = { viewModel.setMaxRuntimeSeconds(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        label = { Text(stringResource(Res.string.network_quality_max_runtime)) },
                        isError = uiState.maxRuntimeError != null,
                        supportingText = uiState.maxRuntimeError?.let {
                            { ErrorText(stringOrRes(it)) }
                        },
                    )
                    Spacer(Modifier.padding(vertical = 8.dp))

                    SwitchLine(
                        label = stringResource(Res.string.network_quality_serial),
                        checked = uiState.serial,
                        onCheckedChange = { viewModel.setSerial(it) },
                    )
                    SwitchLine(
                        label = stringResource(Res.string.network_quality_http3),
                        checked = uiState.http3,
                        onCheckedChange = { viewModel.setHttp3(it) },
                    )

                    if (targetConnected) {
                        OutboundSelector(
                            selectedTag = uiState.outboundTag,
                            onSelect = { viewModel.setOutboundTag(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (uiState.canTest) {
                    Button(onClick = { viewModel.doTest(targetConnected) }) {
                        Text(stringResource(Res.string.start))
                    }
                } else {
                    Button(onClick = { viewModel.cancel() }) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            }

            uiState.report?.let { report ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    NetworkQualityReportContent(report)
                }
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    alert?.let { message ->
        AlertDialog(
            onDismissRequest = { alert = null },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) { alert = null }
            },
            icon = { Icon(vectorResource(Res.drawable.error), null) },
            title = { Text(stringResource(Res.string.error_title)) },
            text = { Text(message) },
        )
    }
}

@Composable
private fun NetworkQualityReportContent(
    report: NetworkQualityReport,
    modifier: Modifier = Modifier,
) {
    val downloadMeasured = report.phase >= NetworkQualityPhase.Download
    val uploadMeasured = report.phase >= NetworkQualityPhase.Upload

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ReportLine(
            label = stringResource(Res.string.idle_latency),
            value = report.idleLatencyMs.takeIf { it > 0 }?.let { "$it ms" },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.download_capacity),
            value = report.downloadCapacity
                .takeIf { downloadMeasured }
                ?.let { stringResource(Res.string.speed, Libcore.formatBytes(it)) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.download_responsiveness),
            value = report.downloadRpm
                .takeIf { downloadMeasured }
                ?.let { stringResource(Res.string.responsiveness_rpm, it.toString()) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.upload_capacity),
            value = report.uploadCapacity
                .takeIf { uploadMeasured }
                ?.let { stringResource(Res.string.speed, Libcore.formatBytes(it)) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.upload_responsiveness),
            value = report.uploadRpm
                .takeIf { uploadMeasured }
                ?.let { stringResource(Res.string.responsiveness_rpm, it.toString()) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.elapsed_time),
            value = report.elapsedMs.takeIf { it > 0 }?.let { "$it ms" },
        )
    }
}

@Composable
private fun ReportLine(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value.orEmpty(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ErrorText(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Preview
@Composable
private fun PreviewNetworkQuality() {
    PreviewContainer {
        NetworkQualityScreen(
            onBackPress = {},
            onOpenRemoteControl = {},
        )
    }
}
