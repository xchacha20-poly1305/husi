package fr.husi.ui.remote

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.withNavigation
import fr.husi.core.remote.RemoteSessionState
import fr.husi.database.RemoteServer
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.cancel
import fr.husi.resources.check
import fr.husi.resources.connected
import fr.husi.resources.connecting
import fr.husi.resources.delete
import fr.husi.resources.edit
import fr.husi.resources.note_add
import fr.husi.resources.phonelink_ring
import fr.husi.resources.remote_control
import fr.husi.resources.remote_delete_confirm
import fr.husi.resources.remote_empty
import fr.husi.resources.remote_reconnecting
import fr.husi.resources.remote_server_add
import fr.husi.resources.remote_target_local
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun RemoteControlScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    onEditServer: (Long) -> Unit,
    viewModel: RemoteControlScreenViewModel = viewModel {
        RemoteControlScreenViewModel()
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<RemoteServer?>(null) }

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
                title = { Text(stringResource(Res.string.remote_control)) },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditServer(0L) }) {
                Icon(
                    imageVector = vectorResource(Res.drawable.note_add),
                    contentDescription = stringResource(Res.string.remote_server_add),
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = innerPadding.withNavigation()
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .fadingEdge(listState),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "local") {
                    RemoteServerCard(
                        title = stringResource(Res.string.remote_target_local),
                        subtitle = null,
                        selected = !uiState.isRemote,
                        onClick = { scope.launch { viewModel.selectLocal() } },
                    )
                }
                if (uiState.servers.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.remote_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(uiState.servers, key = { it.id }) { server ->
                    val selected = uiState.activeServerId == server.id
                    RemoteServerCard(
                        title = server.name.ifBlank { server.url },
                        subtitle = server.url,
                        selected = selected,
                        status = if (selected) uiState.sessionState else null,
                        onClick = { scope.launch { viewModel.selectServer(server) } },
                        onEdit = { onEditServer(server.id) },
                        onDelete = { pendingDelete = server },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
            BoxedVerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState),
                style = defaultMaterialScrollbarStyle().copy(thickness = 12.dp),
            )
        }
    }

    pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(stringResource(Res.string.delete)) {
                    scope.launch {
                        viewModel.deleteServer(server.id)
                        pendingDelete = null
                    }
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    pendingDelete = null
                }
            },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text(stringResource(Res.string.remote_delete_confirm)) },
        )
    }
}

@Composable
private fun RemoteServerCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: RemoteSessionState? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.phonelink_ring),
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                status?.let {
                    Text(
                        text = remoteSessionStatusText(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = vectorResource(Res.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            onEdit?.let {
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.edit),
                    contentDescription = stringResource(Res.string.edit),
                    onClick = it,
                )
            }
            onDelete?.let {
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.delete),
                    contentDescription = stringResource(Res.string.delete),
                    onClick = it,
                )
            }
        }
    }
}

@Composable
internal fun remoteSessionStatusText(state: RemoteSessionState): String {
    return when (state) {
        RemoteSessionState.CONNECTING -> stringResource(Res.string.connecting)
        RemoteSessionState.RECONNECTING -> stringResource(Res.string.remote_reconnecting)
        RemoteSessionState.CONNECTED -> stringResource(Res.string.connected)
    }
}
