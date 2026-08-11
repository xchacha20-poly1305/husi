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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BackHandler
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.ButtonDefaults
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.proto.v1.NATFiltering
import fr.husi.proto.v1.NATMapping
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.available
import fr.husi.resources.back
import fr.husi.resources.cancel
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.latency
import fr.husi.resources.nat_external_address
import fr.husi.resources.nat_filtering
import fr.husi.resources.nat_mapping
import fr.husi.resources.nat_result_hint
import fr.husi.resources.nat_stun_server_hint
import fr.husi.resources.nat_type_detection
import fr.husi.resources.nat_type_not_supported
import fr.husi.resources.route_proxy
import fr.husi.resources.start
import fr.husi.resources.stun_attest_loading
import fr.husi.resources.stun_test
import fr.husi.ui.ensurePreviewRepository
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun StunScreen(
    modifier: Modifier = Modifier,
    viewModel: StunScreenViewModel = viewModel { StunScreenViewModel() },
    onBackPress: () -> Unit,
) {
    fun exit() {
        viewModel.cancel()
        onBackPress()
    }
    BackHandler(true, ::exit)

    var alertMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is StunScreenUiEvent.Alert -> alertMessage = event.message
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
                        onClick = ::exit,
                    )
                },
                title = { Text(stringResource(Res.string.stun_test)) },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = uiState.server,
                    onValueChange = { viewModel.setServer(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    label = { Text(stringResource(Res.string.nat_stun_server_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.proxy,
                    onValueChange = { viewModel.setProxy(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    label = { Text(stringResource(Res.string.route_proxy)) },
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (uiState.isDoing) {
                    Button(
                        onClick = viewModel::cancel,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                } else {
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.doTest()
                        },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(Res.string.start))
                    }
                }
            }

            uiState.report?.let { report ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    StunReportContent(report, uiState.isDoing)
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    alertMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            confirmButton = {
                TextButton(stringResource(Res.string.back)) {
                    alertMessage = null
                }
            },
            icon = { Icon(vectorResource(Res.drawable.error), null) },
            title = { Text(stringResource(Res.string.error_title)) },
            text = { Text(message) },
        )
    }
}

@Composable
private fun StunReportContent(
    report: StunReport,
    isDoing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.nat_result_hint),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (isDoing) {
                    Text(
                        text = stringResource(Res.string.stun_attest_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        ResultValue(
            label = stringResource(Res.string.nat_external_address),
            value = report.externalAddress,
            highlight = true,
        )

        Column {
            ResultLine(
                label = stringResource(Res.string.latency),
                value = report.latencyMs?.let { stringResource(Res.string.available, it) },
            )
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            if (!isDoing && report.natTypeUnsupported) {
                ResultLine(
                    label = stringResource(Res.string.nat_type_detection),
                    value = stringResource(Res.string.nat_type_not_supported),
                )
            } else {
                ResultLine(
                    label = stringResource(Res.string.nat_mapping),
                    value = if (isDoing) {
                        null
                    } else {
                        report.mappingDisplay
                    },
                    color = natMappingColor(report.mapping),
                )
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                ResultLine(
                    label = stringResource(Res.string.nat_filtering),
                    value = if (isDoing) {
                        null
                    } else {
                        report.filteringDisplay
                    },
                    color = natFilteringColor(report.filtering),
                )
            }
        }
    }
}

@Composable
private fun ResultValue(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    color: Color? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (highlight) 72.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        if (value == null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PendingIndicator()
            }
        } else {
            SelectionContainer {
                Text(
                    text = value,
                    color = color
                        ?: if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    style = if (highlight) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = if (highlight) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun ResultLine(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    color: Color? = null,
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
        if (value == null) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                PendingIndicator()
            }
        } else {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    color = color ?: Color.Unspecified,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PendingIndicator(modifier: Modifier = Modifier) {
    CircularWavyProgressIndicator(
        modifier = modifier,
    )
}

private fun natMappingColor(value: NATMapping): Color = when (value) {
    NATMapping.NAT_MAPPING_ENDPOINT_INDEPENDENT -> Color.Green
    NATMapping.NAT_MAPPING_ADDRESS_DEPENDENT -> Color.Yellow
    NATMapping.NAT_MAPPING_ADDRESS_AND_PORT_DEPENDENT -> Color.Red
    else -> Color.Unspecified
}

private fun natFilteringColor(value: NATFiltering): Color = when (value) {
    NATFiltering.NAT_FILTERING_ENDPOINT_INDEPENDENT -> Color.Green
    NATFiltering.NAT_FILTERING_ADDRESS_DEPENDENT -> Color.Yellow
    NATFiltering.NAT_FILTERING_ADDRESS_AND_PORT_DEPENDENT -> Color.Red
    else -> Color.Unspecified
}

@Preview
@Composable
private fun PreviewStunScreen() {
    ensurePreviewRepository()
    val viewModel = viewModel { StunScreenViewModel() }

    StunScreen(
        viewModel = viewModel,
        onBackPress = {},
    )
}
