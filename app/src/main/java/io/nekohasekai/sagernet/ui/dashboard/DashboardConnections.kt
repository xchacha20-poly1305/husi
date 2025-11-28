package io.nekohasekai.sagernet.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.compose.rememberScrollHideState

@Composable
internal fun DashboardConnectionsScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    closeConnection: (uuid: String) -> Unit,
    openDetail: (uuid: String) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val visible by rememberScrollHideState(listState)

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
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
                    Icon(ImageVector.vectorResource(R.drawable.delete_forever), null)
                },
                modifier = Modifier.fillMaxWidth(),
                onDismiss = { closeConnection(connection.uuid) },
            ) {
                ConnectionCard(
                    connection = connection,
                    openDetail = openDetail,
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    modifier: Modifier = Modifier,
    connection: Connection,
    openDetail: (id: String) -> Unit,
) {
    ElevatedCard(
        onClick = { openDetail(connection.uuid) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = connection.network,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(id = R.color.log_green),
            )
            Text(
                text = connection.dst,
                fontSize = 16.sp,
                color = colorResource(id = R.color.color_pink_ssr),
            )
            val host = connection.host
            val showHost = host.isNotBlank() && !connection.dst.startsWith(host)
            if (showHost) Text(
                text = host,
                fontSize = 16.sp,
                color = colorResource(id = R.color.log_red_light),
            )
            Text(
                text = connection.inbound,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondaryFixed,
            )

            Text(
                text = connection.chain,
                fontSize = 14.sp,
                color = colorResource(id = R.color.log_blue),
            )

            Text(
                text = stringResource(
                    R.string.traffic,
                    connection.uploadTotal,
                    connection.downloadTotal,
                ),
                fontSize = 14.sp,
            )

            Text(
                text = stringResource(
                    if (connection.closed) {
                        R.string.connection_status_closed
                    } else {
                        R.string.connection_status_active
                    },
                ),
                fontSize = 14.sp,
                color = if (connection.closed) {
                    Color.Red
                } else {
                    Color.Green
                },
            )
        }
    }
}