package fr.husi.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PasswordPreference
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.withNavigation
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.check
import fr.husi.resources.connecting
import fr.husi.resources.connection_test
import fr.husi.resources.connection_test_error
import fr.husi.resources.done
import fr.husi.resources.emoji_symbols
import fr.husi.resources.error
import fr.husi.resources.link
import fr.husi.resources.not_set
import fr.husi.resources.password
import fr.husi.resources.profile_name
import fr.husi.resources.refresh
import fr.husi.resources.remote_server_add
import fr.husi.resources.remote_server_edit
import fr.husi.resources.remote_server_secret
import fr.husi.resources.remote_server_url_hint
import fr.husi.resources.remote_server_url_invalid
import fr.husi.resources.remote_test_success
import fr.husi.resources.replay
import fr.husi.resources.server_address
import fr.husi.resources.wifi_find
import fr.husi.ui.PreviewContainer
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun RemoteServerEditScreen(
    serverId: Long,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemoteServerEditViewModel = viewModel {
        RemoteServerEditViewModel(serverId)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val listState = rememberLazyListState()
    val title = if (uiState.isNew) {
        stringResource(Res.string.remote_server_add)
    } else {
        stringResource(Res.string.remote_server_edit)
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
                title = { Text(title) },
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.done),
                            contentDescription = stringResource(Res.string.done),
                            onClick = { viewModel.save(onSaved = onBackPress) },
                        )
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    preferenceGroup {
                        TextFieldPreference(
                            value = uiState.name,
                            onValueChange = viewModel::setName,
                            title = { Text(stringResource(Res.string.profile_name)) },
                            textToValue = { it },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.emoji_symbols,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            summary = {
                                Text(
                                    uiState.name.ifBlank {
                                        stringResource(Res.string.not_set)
                                    },
                                )
                            },
                            valueToText = { it },
                        )
                        TextFieldPreference(
                            value = uiState.url,
                            onValueChange = viewModel::setUrl,
                            title = { Text(stringResource(Res.string.server_address)) },
                            textToValue = { it },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.link,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            summary = {
                                Text(
                                    if (uiState.urlError) {
                                        stringResource(Res.string.remote_server_url_invalid)
                                    } else {
                                        uiState.url.ifBlank {
                                            stringResource(Res.string.remote_server_url_hint)
                                        }
                                    },
                                )
                            },
                            valueToText = { it },
                        )
                        PasswordPreference(
                            value = uiState.secret,
                            onValueChange = viewModel::setSecret,
                            title = { Text(stringResource(Res.string.remote_server_secret)) },
                            icon = {
                                MaskedIcon(
                                    resource = Res.drawable.password,
                                    color = IconMaskColors.IconCoral,
                                    shape = IconMaskShapes.credential(),
                                )
                            },
                        )
                    }
                    item {
                        ConnectionTestStatus(
                            state = uiState.test,
                            onRetry = viewModel::testConnection,
                        )
                    }
                }
                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(thickness = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectionTestStatus(
    state: RemoteServerTestState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val indicatorSize = 20.dp
    val message = when (state) {
        RemoteServerTestState.Idle -> stringResource(Res.string.connection_test)
        RemoteServerTestState.Testing -> stringResource(Res.string.connecting)
        RemoteServerTestState.InvalidURL -> stringResource(Res.string.remote_server_url_invalid)
        is RemoteServerTestState.Success -> stringResource(
            Res.string.remote_test_success,
            state.version,
        )
        is RemoteServerTestState.Failure -> stringResource(
            Res.string.connection_test_error,
            state.message,
        )
    }
    val contentColor = when (state) {
        is RemoteServerTestState.Success -> MaterialTheme.colorScheme.primary
        RemoteServerTestState.InvalidURL,
        is RemoteServerTestState.Failure,
            -> MaterialTheme.colorScheme.error

        RemoteServerTestState.Idle,
        RemoteServerTestState.Testing,
            -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(indicatorSize),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                RemoteServerTestState.Testing -> LoadingIndicator(
                    modifier = Modifier.size(indicatorSize),
                )

                is RemoteServerTestState.Success -> Icon(
                    imageVector = vectorResource(Res.drawable.check),
                    contentDescription = null,
                    tint = contentColor,
                )

                RemoteServerTestState.InvalidURL,
                is RemoteServerTestState.Failure,
                    -> Icon(
                    imageVector = vectorResource(Res.drawable.error),
                    contentDescription = null,
                    tint = contentColor,
                )

                RemoteServerTestState.Idle -> Icon(
                    imageVector = vectorResource(Res.drawable.wifi_find),
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        }
        Text(
            text = message,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        SimpleIconButton(
            imageVector = vectorResource(Res.drawable.replay),
            contentDescription = stringResource(Res.string.refresh),
            enabled = state != RemoteServerTestState.Testing,
            onClick = onRetry,
        )
    }
}

@Preview
@Composable
private fun PreviewConnectionTestStatus() {
    PreviewContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ConnectionTestStatus(state = RemoteServerTestState.Idle, onRetry = {})
            ConnectionTestStatus(state = RemoteServerTestState.Testing, onRetry = {})
            ConnectionTestStatus(state = RemoteServerTestState.InvalidURL, onRetry = {})
            ConnectionTestStatus(
                state = RemoteServerTestState.Success(version = "1.13.0"),
                onRetry = {},
            )
            ConnectionTestStatus(
                state = RemoteServerTestState.Failure(message = "connection refused"),
                onRetry = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewRemoteServerEditScreen() {
    PreviewContainer {
        RemoteServerEditScreen(
            serverId = 0L,
            onBackPress = {},
        )
    }
}
