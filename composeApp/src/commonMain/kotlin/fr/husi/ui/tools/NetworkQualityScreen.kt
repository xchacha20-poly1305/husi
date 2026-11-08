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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.ScrollableDropdownMenuPopup
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.repository.Repository
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.cancel
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.network_quality_config_url
import fr.husi.resources.network_quality_http3
import fr.husi.resources.network_quality_max_runtime
import fr.husi.resources.network_quality_serial
import fr.husi.resources.network_quality_test
import fr.husi.resources.ok
import fr.husi.resources.start
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.getStringOrRes
import fr.husi.ui.stringOrRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

@Composable
internal fun NetworkQualityScreenContent(
    viewModel: NetworkQualityPresenter,
    targetConnected: Boolean,
    onBackPress: () -> Unit,
    targetMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    outboundSelector: @Composable (String, (String) -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<Repository>()
    val snackbar = LocalSnackbarEmitter.current
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandMenu by remember { mutableStateOf(false) }

    var alert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel, repository) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is NetworkQualityScreenUiEvent.Snackbar -> snackbar.show(event.message)

                is NetworkQualityScreenUiEvent.ErrorAlert -> {
                    alert = repository.getStringOrRes(event.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                hazeState = null,
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
                            ScrollableDropdownMenuPopup(
                                expanded = expandMenu,
                                onDismissRequest = { expandMenu = false },
                            ) {
                                targetMenu { expandMenu = false }
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
            modifier =
                Modifier.fillMaxSize()
                    .paddingExceptBottom(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value = uiState.configUrl,
                        onValueChange = { viewModel.setConfigUrl(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        label = { Text(stringResource(Res.string.network_quality_config_url)) },
                        singleLine = false,
                        isError = uiState.urlError != null,
                        supportingText = uiState.urlError?.let { { ErrorText(stringOrRes(it)) } },
                    )
                    Spacer(Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(
                        value = uiState.maxRuntimeSeconds.toString(),
                        onValueChange = { viewModel.setMaxRuntimeSeconds(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        label = { Text(stringResource(Res.string.network_quality_max_runtime)) },
                        isError = uiState.maxRuntimeError != null,
                        supportingText =
                            uiState.maxRuntimeError?.let {
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
                        outboundSelector(
                            uiState.outboundTag,
                            viewModel::setOutboundTag,
                            Modifier.fillMaxWidth().padding(8.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
