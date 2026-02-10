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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.TextButton
import fr.husi.compose.paddingExceptBottom
import fr.husi.repository.FakeRepository
import fr.husi.repository.repo
import fr.husi.ui.getStringOrRes
import kotlinx.coroutines.launch
import fr.husi.resources.*
import fr.husi.ui.stringOrRes
import fr.husi.libcore.Libcore

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpeedtestScreen(
    modifier: Modifier = Modifier,
    viewModel: SpeedTestScreenViewModel = viewModel { SpeedTestScreenViewModel() },
    onBackPress: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var alert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is SpeedTestScreenUiEvent.Snackbar -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = repo.getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is SpeedTestScreenUiEvent.ErrorAlert -> {
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
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.speed_test)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SingleChoiceSegmentedButtonRow {
                        val isDownload =
                            uiState.mode == SpeedTestScreenViewModel.SpeedTestMode.Download
                        SegmentedButton(
                            selected = isDownload,
                            onClick = { viewModel.setMode(SpeedTestScreenViewModel.SpeedTestMode.Download) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = isDownload,
                                    inactiveContent = {
                                        Icon(vectorResource(Res.drawable.download), null)
                                    },
                                )
                            },
                            label = { Text(stringResource(Res.string.download)) },
                        )
                        val isUpload =
                            uiState.mode == SpeedTestScreenViewModel.SpeedTestMode.Upload
                        SegmentedButton(
                            selected = isUpload,
                            onClick = { viewModel.setMode(SpeedTestScreenViewModel.SpeedTestMode.Upload) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = isUpload,
                                    inactiveContent = {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.file_upload),
                                            contentDescription = null,
                                        )
                                    },
                                )
                            },
                            label = { Text(stringResource(Res.string.upload)) },
                        )
                    }

                    OutlinedTextField(
                        value = when (uiState.mode) {
                            SpeedTestScreenViewModel.SpeedTestMode.Download -> uiState.downloadURL
                            SpeedTestScreenViewModel.SpeedTestMode.Upload -> uiState.uploadURL
                        },
                        onValueChange = { viewModel.setServer(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        label = { Text(stringResource(Res.string.server_address)) },
                        singleLine = false,
                        isError = uiState.urlError != null,
                        supportingText = uiState.urlError?.let {
                            {
                                Text(
                                    text = stringOrRes(it),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(
                        value = uiState.timeout.toString(),
                        onValueChange = { viewModel.setTimeout(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        label = { Text(stringResource(Res.string.test_timeout)) },
                        isError = uiState.timeoutError != null,
                        supportingText = uiState.timeoutError?.let {
                            {
                                Text(
                                    text = stringOrRes(it),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.padding(vertical = 8.dp))

                    if (uiState.mode == SpeedTestScreenViewModel.SpeedTestMode.Upload) {
                        OutlinedTextField(
                            value = uiState.uploadLength.toString(),
                            onValueChange = { viewModel.setUploadSize(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            label = { Text(stringResource(Res.string.upload_size)) },
                            isError = uiState.uploadLengthError != null,
                            supportingText = uiState.timeoutError?.let {
                                {
                                    Text(
                                        text = stringOrRes(it),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp),
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { viewModel.doSpeedTest() },
                    enabled = uiState.progress == null,
                ) {
                    Text(stringResource(Res.string.start))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .size(64.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                val speed = Libcore.formatBytes(uiState.speed)
                SelectionContainer {
                    Text(
                        text = stringResource(Res.string.speed, speed),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            uiState.progress?.let {
                LinearWavyProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (alert != null) AlertDialog(
        onDismissRequest = { alert = null },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                alert = null
            }
        },
        icon = { Icon(vectorResource(Res.drawable.error), null) },
        title = { Text(stringResource(Res.string.error_title)) },
        text = { Text(alert!!) },
    )
}

@Preview
@Composable
private fun PreviewSpeedtest() {
    repo = FakeRepository()

    SpeedtestScreen(
        viewModel = SpeedTestScreenViewModel(),
        onBackPress = {},
    )
}
