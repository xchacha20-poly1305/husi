package io.nekohasekai.sagernet.ui.dashboard

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.compose.theme.LogColors

@Composable
internal fun DashboardConnectionsScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    searchTextFieldState: TextFieldState,
    bottomPadding: Dp,
    resolveProcessInfo: suspend (String?, Int) -> ProcessInfo?,
    closeConnection: (uuid: String) -> Unit,
    openDetail: (uuid: String) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    onClearSearch: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val visible by rememberScrollHideState(listState)
    val canScroll by remember {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }
    val searchBarVisible = visible && (canScroll || searchTextFieldState.text.isNotEmpty())
    onVisibleChange(visible)

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                imageVector = ImageVector.vectorResource(R.drawable.delete_forever),
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

        AnimatedVisibility(
            visible = searchBarVisible,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        state = searchTextFieldState,
                        onSearch = { focusManager.clearFocus() },
                        expanded = false,
                        onExpandedChange = {},
                        leadingIcon = {
                            Icon(ImageVector.vectorResource(R.drawable.search), null)
                        },
                        trailingIcon = if (searchTextFieldState.text.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = onClearSearch,
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.close),
                                        contentDescription = stringResource(android.R.string.cancel),
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
                modifier = Modifier.padding(top = 24.dp),
                colors = SearchBarDefaults.colors().run {
                    copy(
                        containerColor = containerColor.copy(alpha = 0.8f),
                    )
                },
                content = {},
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    modifier: Modifier = Modifier,
    connection: ConnectionDetailState,
    resolveProcessInfo: suspend (String?, Int) -> ProcessInfo?,
    openDetail: (id: String) -> Unit,
) {
    val context = LocalContext.current
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
                        R.string.traffic,
                        Formatter.formatFileSize(context, connection.uploadTotal),
                        Formatter.formatFileSize(context, connection.downloadTotal),
                    ),
                    fontSize = 14.sp,
                )

                Text(
                    text = stringResource(
                        if (connection.isClosed) {
                            R.string.connection_status_closed
                        } else {
                            R.string.connection_status_active
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
                Image(
                    painter = rememberDrawablePainter(icon),
                    contentDescription = processInfo?.label,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
