package fr.husi.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.husi.compose.theme.LogColors
import fr.husi.resources.*
import fr.husi.libcore.Libcore
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DashboardConnectionsScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    searchTextFieldState: TextFieldState,
    bottomPadding: Dp,
    searchBarBottomSpacing: Dp,
    resolveProcessInfo: suspend (String?, Int) -> ProcessInfo?,
    closeConnection: (uuid: String) -> Unit,
    openDetail: (uuid: String) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    onClearSearch: () -> Unit,
) {
    val itemSpacing = 12.dp
    val density = LocalDensity.current
    var searchBarHeightPx by remember { mutableIntStateOf(0) }
    val searchBarHeight = with(density) { searchBarHeightPx.toDp() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val searchBarBottomPadding = bottomPadding + searchBarBottomSpacing
    val listBottomPadding = searchBarBottomPadding + searchBarHeight + itemSpacing
    LaunchedEffect(Unit) {
        onVisibleChange(true)
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            items(
                items = uiState.connections,
                key = { it.uuid },
                contentType = { 0 },
            ) { connection ->
                val swipState = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    state = swipState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.delete_forever),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier.fillMaxWidth(),
                    onDismiss = { swipeToDismissBoxValue ->
                        if (swipeToDismissBoxValue == SwipeToDismissBoxValue.EndToStart) {
                            closeConnection(connection.uuid)
                        }
                    },
                ) {
                    ConnectionCard(
                        connection = connection,
                        resolveProcessInfo = resolveProcessInfo,
                        openDetail = openDetail,
                    )
                }
            }
        }

        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    state = searchTextFieldState,
                    onSearch = { focusManager.clearFocus() },
                    expanded = false,
                    onExpandedChange = {},
                    leadingIcon = {
                        Icon(vectorResource(Res.drawable.search), null)
                    },
                    trailingIcon = if (searchTextFieldState.text.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = onClearSearch,
                            ) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.close),
                                    contentDescription = stringResource(Res.string.cancel),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = searchBarBottomPadding)
                .onSizeChanged { searchBarHeightPx = it.height },
            colors = SearchBarDefaults.colors().run {
                copy(
                    containerColor = containerColor.copy(alpha = 0.8f),
                )
            },
            content = {},
        )
    }
}

@Composable
private fun ConnectionCard(
    modifier: Modifier = Modifier,
    connection: ConnectionDetailState,
    resolveProcessInfo: suspend (String?, Int) -> ProcessInfo?,
    openDetail: (id: String) -> Unit,
) {
    val process = connection.process
    val uid = connection.uid
    var processInfo by remember { mutableStateOf<ProcessInfo?>(null) }
    // No keys because LazyColumn's item keys handle it
    LaunchedEffect(Unit) {
        processInfo = resolveProcessInfo(process, uid)
    }

    ElevatedCard(
        onClick = { openDetail(connection.uuid) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (connection.protocol == null) {
                        connection.network
                    } else {
                        "${connection.network}/${connection.protocol}"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LogColors.green,
                )
                Text(
                    text = connection.dst,
                    fontSize = 16.sp,
                    color = Color(0xFFFB7299), // Pink
                )
                val host = connection.host
                val showHost = host.isNotBlank() && !connection.dst.startsWith(host)
                if (showHost) Text(
                    text = host,
                    fontSize = 16.sp,
                    color = LogColors.redLight,
                )
                Text(
                    text = connection.inbound,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondaryFixed,
                )

                Text(
                    text = connection.chain,
                    fontSize = 14.sp,
                    color = LogColors.blue,
                )

                Text(
                    text = stringResource(
                        Res.string.traffic,
                        Libcore.formatBytes(connection.uploadTotal),
                        Libcore.formatBytes(connection.downloadTotal),
                    ),
                    fontSize = 14.sp,
                )

                Text(
                    text = stringResource(
                        if (connection.isClosed) {
                            Res.string.connection_status_closed
                        } else {
                            Res.string.connection_status_active
                        },
                    ),
                    fontSize = 14.sp,
                    color = if (connection.isClosed) {
                        Color.Red
                    } else {
                        Color.Green
                    },
                )
            }
            processInfo?.icon?.let { icon ->
                ProcessIcon(
                    icon = icon,
                    contentDescription = processInfo?.label,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
