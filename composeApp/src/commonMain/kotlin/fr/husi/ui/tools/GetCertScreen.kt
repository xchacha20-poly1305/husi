package fr.husi.ui.tools

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboard
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.DropDownSelector
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.TooltipIconButton
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.paddingExceptBottom
import fr.husi.compose.setPlainText
import fr.husi.ktx.readableMessage
import fr.husi.repository.FakeRepository
import fr.husi.repository.repo
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GetCertScreen(
    modifier: Modifier = Modifier,
    viewModel: GetCertScreenViewModel = viewModel { GetCertScreenViewModel() },
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val clipboard = LocalClipboard.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.get_cert)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBack,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        GetCertContent(
            modifier = Modifier.paddingExceptBottom(innerPadding),
            viewModel = viewModel,
            copyToClipboard = {
                scope.launch {
                    clipboard.setPlainText(it)
                    snackbarHostState.showSnackbar(
                        message = repo.getString(Res.string.copy_success),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GetCertContent(
    modifier: Modifier,
    viewModel: GetCertScreenViewModel,
    copyToClipboard: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var alert by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    LaunchedEffect(uiState.alert) {
        alert = uiState.alert?.readableMessage
    }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState),
        ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = uiState.server,
                    onValueChange = { viewModel.setServer(it) },
                    label = { Text(stringResource(Res.string.get_cert_server_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.serverName,
                    onValueChange = { viewModel.setServerName(it) },
                    label = { Text(stringResource(Res.string.sni)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))

                DropDownSelector(
                    label = { Text(stringResource(Res.string.protocol)) },
                    value = uiState.protocol,
                    values = listOf("https", "quic"),
                    onValueChange = { viewModel.setProtocol(it) },
                    displayValue = { it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))

                DropDownSelector(
                    label = { Text(stringResource(Res.string.format)) },
                    value = uiState.format,
                    values = Format.entries,
                    onValueChange = { viewModel.setFormat(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.proxy,
                    onValueChange = { viewModel.setProxy(it) },
                    label = { Text(stringResource(Res.string.route_proxy)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = { viewModel.launch() },
                enabled = !uiState.isDoing,
            ) {
                Text(stringResource(Res.string.start))
            }
        }

        if (uiState.formatted.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    CopyButton {
                        copyToClipboard(uiState.formatted)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = uiState.formatted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .animateContentSize(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Crossfade(
                    targetState = uiState.isDoing,
                    animationSpec = tween(durationMillis = 300),
                ) { isDoing ->
                    if (isDoing) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) { LoadingIndicator() }
                    } else {
                        Column {
                            CopyButton {
                                copyToClipboard(uiState.cert)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SelectionContainer {
                                Text(
                                    text = uiState.cert,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        BoxedVerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = scrollState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
    }

    if (alert != null) AlertDialog(
        onDismissRequest = { alert = null },
        confirmButton = {
            TextButton(onClick = { alert = null }) {
                Text(stringResource(Res.string.ok))
            }
        },
        icon = { Icon(vectorResource(Res.drawable.error), null) },
        title = { Text(stringResource(Res.string.error_title)) },
        text = { Text(alert!!) },
    )
}

@Composable
private fun CopyButton(modifier: Modifier = Modifier, copy: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TooltipIconButton(
            onClick = copy,
            icon = vectorResource(Res.drawable.content_copy),
            contentDescription = stringResource(Res.string.action_copy),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewGetCert() {
    repo = FakeRepository()

    GetCertScreen(
        viewModel = GetCertScreenViewModel(),
        onBack = {},
    )
}
