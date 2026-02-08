package io.nekohasekai.sagernet.ui.tools

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.repository.TempRepository
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.ComposeActivity
import io.nekohasekai.sagernet.ui.getStringOrRes
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SpeedtestActivity : ComposeActivity() {

    private val viewModel by viewModels<SpeedTestActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            viewModel.initialize()
        }

        setContent {
            AppTheme {
                SpeedtestScreen(
                    viewModel = viewModel,
                    onBackPress = {
                        onBackPressedDispatcher.onBackPressed()
                    },
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedtestScreen(
    modifier: Modifier = Modifier,
    viewModel: SpeedTestActivityViewModel,
    onBackPress: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var alert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is SpeedTestActivityUiEvent.Snackbar -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is SpeedTestActivityUiEvent.ErrorAlert -> {
                    alert = context.getStringOrRes(event.message)
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
                title = { Text(stringResource(R.string.speed_test)) },
                navigationIcon = ImageVector.vectorResource(R.drawable.arrow_back),
                navigationDescription = stringResource(R.string.back),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
                onNavigationClick = onBackPress,
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
                            uiState.mode == SpeedTestActivityViewModel.SpeedTestMode.Download
                        SegmentedButton(
                            selected = isDownload,
                            onClick = { viewModel.setMode(SpeedTestActivityViewModel.SpeedTestMode.Download) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = isDownload,
                                    inactiveContent = {
                                        Icon(ImageVector.vectorResource(R.drawable.download), null)
                                    },
                                )
                            },
                            label = { Text(stringResource(R.string.download)) },
                        )
                        val isUpload =
                            uiState.mode == SpeedTestActivityViewModel.SpeedTestMode.Upload
                        SegmentedButton(
                            selected = isUpload,
                            onClick = { viewModel.setMode(SpeedTestActivityViewModel.SpeedTestMode.Upload) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = isUpload,
                                    inactiveContent = {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.file_upload),
                                            contentDescription = null,
                                        )
                                    },
                                )
                            },
                            label = { Text(stringResource(R.string.upload)) },
                        )
                    }

                    OutlinedTextField(
                        value = when (uiState.mode) {
                            SpeedTestActivityViewModel.SpeedTestMode.Download -> uiState.downloadURL
                            SpeedTestActivityViewModel.SpeedTestMode.Upload -> uiState.uploadURL
                        },
                        onValueChange = { viewModel.setServer(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        label = { Text(stringResource(R.string.server_address)) },
                        singleLine = false,
                        isError = uiState.urlError != null,
                        supportingText = uiState.urlError?.let {
                            {
                                Text(
                                    text = LocalContext.current.getStringOrRes(it),
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
                        label = { Text(stringResource(R.string.test_timeout)) },
                        isError = uiState.timeoutError != null,
                        supportingText = uiState.timeoutError?.let {
                            {
                                Text(
                                    text = LocalContext.current.getStringOrRes(it),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.padding(vertical = 8.dp))

                    if (uiState.mode == SpeedTestActivityViewModel.SpeedTestMode.Upload) {
                        OutlinedTextField(
                            value = uiState.uploadLength.toString(),
                            onValueChange = { viewModel.setUploadSize(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            label = { Text(stringResource(R.string.upload_size)) },
                            isError = uiState.uploadLengthError != null,
                            supportingText = uiState.timeoutError?.let {
                                {
                                    Text(
                                        text = LocalContext.current.getStringOrRes(it),
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
                    Text(stringResource(R.string.start))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .size(64.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                val speed = Formatter.formatFileSize(LocalContext.current, uiState.speed)
                SelectionContainer {
                    Text(
                        text = stringResource(R.string.speed, speed),
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
            TextButton(stringResource(android.R.string.ok)) {
                alert = null
            }
        },
        icon = { Icon(ImageVector.vectorResource(R.drawable.error), null) },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(alert!!) },
    )
}

@Preview
@Composable
private fun PreviewSpeedtest() {
    val context = LocalContext.current
    repo = TempRepository(context)

    SpeedtestScreen(
        viewModel = SpeedTestActivityViewModel(),
        onBackPress = {},
    )
}