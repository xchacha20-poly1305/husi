package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BackHandler
import fr.husi.compose.KeyValueLine
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.ButtonDefaults
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.libcore.Libcore
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
import fr.husi.resources.stun_test
import fr.husi.ui.ensurePreviewRepository
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.stun_test)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = ::exit,
                    )
                },
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
        modifier = modifier.padding(16.dp),
    ) {
        Text(stringResource(Res.string.nat_result_hint))

        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            KeyValueLine(
                key = stringResource(Res.string.nat_external_address),
                value = report.externalAddress,
                onNullValue = { Indicator() },
            )
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            KeyValueLine(
                key = stringResource(Res.string.latency),
                value = report.latencyMs?.let { stringResource(Res.string.available, it) },
                onNullValue = { Indicator() },
            )
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            if (!isDoing && report.natTypeUnsupported) {
                KeyValueLine(
                    key = stringResource(Res.string.nat_type_detection),
                    value = stringResource(Res.string.nat_type_not_supported),
                )
            } else {
                KeyValueLine(
                    key = stringResource(Res.string.nat_mapping),
                    value = if (isDoing) {
                        null
                    } else {
                        Libcore.formatNATMapping(report.natMapping)
                    },
                    color = natMappingColor(report.natMapping),
                )
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                KeyValueLine(
                    key = stringResource(Res.string.nat_filtering),
                    value = if (isDoing) {
                        null
                    } else {
                        Libcore.formatNATFiltering(report.natFiltering)
                    },
                    color = natFilteringColor(report.natFiltering),
                )
            }
        }
    }
}

@Composable
private fun Indicator(modifier: Modifier = Modifier) {
    // Too small to use wavy
    CircularProgressIndicator(
        modifier = modifier.size(16.dp),
        strokeWidth = 2.dp,
    )
}

private fun natMappingColor(value: Int): Color = when (value) {
    Libcore.NATMappingEndpointIndependent -> Color.Green
    Libcore.NATMappingAddressDependent -> Color.Yellow
    Libcore.NATMappingAddressAndPortDependent -> Color.Red
    else -> Color.Unspecified
}

private fun natFilteringColor(value: Int): Color = when (value) {
    Libcore.NATFilteringEndpointIndependent -> Color.Green
    Libcore.NATFilteringAddressDependent -> Color.Yellow
    Libcore.NATFilteringAddressAndPortDependent -> Color.Red
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
