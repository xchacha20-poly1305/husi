@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ProxySet
import io.nekohasekai.sagernet.aidl.ProxySetItem
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.colorForUrlTestDelay

@Composable
internal fun DashboardProxySetScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = uiState.proxySets,
            key = { it.type + it.tag },
            contentType = { 0 },
        ) { proxySet ->
            ProxySetCard(
                proxySet = proxySet,
                selectProxy = selectProxy,
                urlTestForGroup = urlTestForGroup,
            )
        }
    }
}

@Composable
private fun ProxySetCard(
    modifier: Modifier = Modifier,
    proxySet: ProxySet,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = proxySet.type,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = proxySet.tag,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                Row {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.bolt),
                        contentDescription = stringResource(R.string.connection_test_url),
                        onClick = { urlTestForGroup(proxySet.tag) },
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(
                            if (expanded) {
                                R.drawable.expand_less
                            } else {
                                R.drawable.expand_more
                            },
                        ),
                        contentDescription = stringResource(R.string.expand),
                        onClick = { expanded = !expanded },
                    )
                }
            }

            if (expanded) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                ) {
                    items(
                        items = proxySet.items,
                        key = { it.type + it.tag },
                        contentType = { 0 },
                    ) { proxy ->
                        val selected = proxySet.selected == proxy.tag
                        ProxyCard(
                            proxy = proxy,
                            selected = selected,
                            selectable = proxySet.selectable,
                            onClick = { selectProxy(proxySet.tag, proxy.tag) },
                        )
                    }
                }
            } else {
                Text(
                    text = proxySet.selected,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProxyCard(
    modifier: Modifier = Modifier,
    proxy: ProxySetItem,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = selectable,
    ) {
        Row {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column {
                Text(
                    text = proxy.type,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                Text(
                    text = proxy.tag,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // TODO URL test for single proxy
                if (proxy.urlTestDelay > 0) Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = proxy.urlTestDelay.toString(),
                        color = colorForUrlTestDelay(proxy.urlTestDelay),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}