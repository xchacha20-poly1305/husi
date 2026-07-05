@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.CardDefaults
import fr.husi.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import fr.husi.compose.material3.Surface
import fr.husi.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.colorForUrlTestDelay
import fr.husi.compose.rememberScrollHideState
import fr.husi.resources.Res
import fr.husi.resources.bolt
import fr.husi.resources.connection_test
import fr.husi.resources.expand
import fr.husi.resources.expand_less
import fr.husi.resources.expand_more
import fr.husi.resources.selected
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun DashboardProxySetScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    bottomPadding: Dp,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestForSingle: (tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val visible by rememberScrollHideState(listState)

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = uiState.proxySets,
                key = { it.type + it.tag },
                contentType = { 0 },
            ) { proxySet ->
                ProxySetCard(
                    proxySet = proxySet,
                    selectProxy = selectProxy,
                    urlTestSingle = urlTestForSingle,
                    urlTestForGroup = urlTestForGroup,
                )
            }
        }

        BoxedVerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
    }
}

@Composable
private fun ProxySetCard(
    modifier: Modifier = Modifier,
    proxySet: ProxySet,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestSingle: (tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedProxy = proxySet.items.find { it.tag == proxySet.selected }
    val selectedDelay = selectedProxy?.urlTestDelay ?: 0
    val urlTestProgress = proxySet.urlTestProgress

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column {
            urlTestProgress?.let { progress ->
                val progressFraction = if (progress.total > 0) {
                    (progress.current.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearWavyProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
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
                        urlTestProgress?.let { progress ->
                            Text(
                                text = "${progress.current} / ${progress.total}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.bolt),
                            contentDescription = stringResource(Res.string.connection_test),
                            modifier = Modifier.then(
                                if (proxySet.isTesting) {
                                    val transition = rememberInfiniteTransition(label = "testing")
                                    val alpha = transition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.2f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(500),
                                            repeatMode = RepeatMode.Reverse,
                                        ),
                                        label = "alpha",
                                    ).value
                                    Modifier.alpha(alpha)
                                } else {
                                    Modifier
                                },
                            ),
                            enabled = !proxySet.isTesting,
                            onClick = { urlTestForGroup(proxySet.tag) },
                        )
                        SimpleIconButton(
                            imageVector = vectorResource(
                                if (expanded) {
                                    Res.drawable.expand_less
                                } else {
                                    Res.drawable.expand_more
                                },
                            ),
                            contentDescription = stringResource(Res.string.expand),
                            onClick = { expanded = !expanded },
                        )
                    }
                }

                if (expanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        proxySet.items.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { proxy ->
                                    val selected = proxySet.selected == proxy.tag
                                    ProxyCard(
                                        modifier = Modifier.weight(1f),
                                        proxy = proxy,
                                        selected = selected,
                                        selectable = proxySet.selectable,
                                        select = { selectProxy(proxySet.tag, proxy.tag) },
                                        urlTest = { urlTestSingle(proxy.tag) },
                                    )
                                }
                                // Fill remaining space if odd number of items
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.selected),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    text = proxySet.selected,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                )
                            }
                            ItemURLTestButton(
                                delay = selectedDelay,
                                onClick = { urlTestSingle(proxySet.selected) },
                            )
                        }
                    }
                }
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
    select: () -> Unit,
    urlTest: () -> Unit,
) {
    Card(
        onClick = select,
        modifier = modifier.fillMaxWidth(),
        enabled = selectable,
        shape = CardDefaults.elevatedShape,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
            }
            ItemURLTestButton(
                delay = proxy.urlTestDelay,
                onClick = urlTest,
            )
        }
    }
}

@Composable
private fun ItemURLTestButton(
    modifier: Modifier = Modifier,
    delay: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .width(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = Color.Black,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (delay > 0) {
                Text(
                    text = delay.toString(),
                    color = colorForUrlTestDelay(delay),
                )
            } else {
                Icon(
                    vectorResource(Res.drawable.bolt),
                    stringResource(Res.string.connection_test),
                )
            }
        }
    }
}
