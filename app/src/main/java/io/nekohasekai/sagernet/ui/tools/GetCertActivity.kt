package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.compose.DropDownSelector
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ui.ComposeActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class GetCertActivity : ComposeActivity() {

    private val viewModel by viewModels<GetCertActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                GetCertScreen(
                    viewModel = viewModel,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    getStringResource = this::getString,
                    copyToClipboard = { cert -> SagerNet.trySetPrimaryClip(cert) },
                )
            }
        }

    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GetCertScreen(
    viewModel: GetCertActivityViewModel,
    onBack: () -> Unit,
    getStringResource: (Int) -> String,
    copyToClipboard: (String) -> Boolean,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = R.string.get_cert,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
                onNavigationClick = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        GetCertContent(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
            launchSnackBar = { success ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = getStringResource(
                            if (success) {
                                R.string.copy_success
                            } else {
                                R.string.copy_failed
                            }
                        ),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            copyToClipboard = copyToClipboard
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GetCertContent(
    modifier: Modifier,
    viewModel: GetCertActivityViewModel,
    launchSnackBar: (success: Boolean) -> Unit,
    copyToClipboard: (String) -> Boolean,
) {
    val state by viewModel.uiState.collectAsState()
    var alert by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is GetCertUiEvent.Alert -> alert = event.e.readableMessage
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                    value = state.server,
                    onValueChange = { viewModel.setServer(it) },
                    label = { Text(stringResource(R.string.get_cert_server_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.serverName,
                    onValueChange = { viewModel.setServerName(it) },
                    label = { Text(stringResource(R.string.sni)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                DropDownSelector(
                    label = { Text(stringResource(R.string.protocol)) },
                    value = state.protocol,
                    values = listOf("https", "quic"),
                    onValueChange = { viewModel.setProtocol(it) },
                    displayValue = { it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                DropDownSelector(
                    label = { Text(stringResource(R.string.format)) },
                    value = state.format,
                    values = listOf("raw", "v2ray", "hysteria"),
                    onValueChange = { viewModel.setFormat(it) },
                    displayValue = { it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.proxy,
                    onValueChange = { viewModel.setProxy(it) },
                    label = { Text(stringResource(R.string.route_proxy)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                enabled = !state.isDoing,
            ) {
                Text(stringResource(R.string.start))
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
                    targetState = state.isDoing,
                    animationSpec = tween(durationMillis = 300),
                ) { isDoing ->
                    if (isDoing) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) { LoadingIndicator() }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
                                    onClick = {
                                        val success = copyToClipboard(state.cert)
                                        launchSnackBar(success)
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.action_copy),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SelectionContainer {
                                Text(text = state.cert)
                            }
                        }
                    }
                }
            }
        }
    }

    if (alert != null) AlertDialog(
        onDismissRequest = { alert = null },
        confirmButton = {
            TextButton(onClick = { alert = null }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        icon = { Icon(Icons.Filled.Error, null) },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(alert!!) },
    )
}
