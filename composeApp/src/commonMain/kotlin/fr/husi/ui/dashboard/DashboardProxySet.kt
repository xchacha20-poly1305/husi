@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalGridApi::class)

package fr.husi.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.colorForUrlTestDelay
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Surface
import fr.husi.compose.material3.Text
import fr.husi.compose.platformCombinedClickable
import fr.husi.resources.Res
import fr.husi.resources.bolt
import fr.husi.resources.clear_search
import fr.husi.resources.close
import fr.husi.resources.connection_test
import fr.husi.resources.expand
import fr.husi.resources.expand_less
import fr.husi.resources.expand_more
import fr.husi.resources.not_found
import fr.husi.resources.outbound
import fr.husi.resources.proxy_set
import fr.husi.resources.search
import fr.husi.resources.search_go
import fr.husi.resources.selected
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun DashboardProxySetScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    contentPadding: PaddingValues,
    searchTextFieldState: TextFieldState,
    setSearchMode: (ProxySearchMode) -> Unit,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestForSingle: (tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
) {
    val listState = rememberLazyListState()
    val visibleProxySets = uiState.proxySets

    Column(modifier = modifier.fillMaxSize()) {
        ProxySetSearchBar(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
            textFieldState = searchTextFieldState,
            searchMode = uiState.proxySearchMode,
            onSearchModeChange = setSearchMode,
        )

        if (visibleProxySets.isEmpty()) {
            NoProxyMatch(modifier = Modifier.fillMaxSize())
            return@Column
        }

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = visibleProxySets,
                    key = { it.id },
                    contentType = { 0 },
                ) { proxySet ->
                    ProxySetCard(
                        proxySet = proxySet,
                        isRemote = uiState.isRemote,
                        urlTestingTags = uiState.urlTestingTags,
                        forceExpanded = uiState.isSearchingOutbounds,
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
}

@Composable
private fun ProxySetSearchBar(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    searchMode: ProxySearchMode,
    onSearchModeChange: (ProxySearchMode) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProxySearchField(textFieldState = textFieldState)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = ProxySearchMode.entries
            for ((index, mode) in modes.withIndex()) {
                val text = when (mode) {
                    ProxySearchMode.PROXY_SET -> Res.string.proxy_set
                    ProxySearchMode.OUTBOUND -> Res.string.outbound
                }
                SegmentedButton(
                    selected = searchMode == mode,
                    onClick = { onSearchModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = { Text(stringResource(text)) },
                )
            }
        }
    }
}

@Composable
private fun NoProxyMatch(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = stringResource(Res.string.not_found),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProxySetCard(
    modifier: Modifier = Modifier,
    proxySet: ProxySet,
    isRemote: Boolean,
    urlTestingTags: Map<String, Int>,
    forceExpanded: Boolean,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestSingle: (tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
) {
    var expandedByUser by rememberSaveable { mutableStateOf(false) }
    val searchTextFieldState = rememberTextFieldState()
    val expanded = expandedByUser || forceExpanded
    var selectedProxyMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedProxy = proxySet.items.find { it.tag == proxySet.selected }
    val selectedDelay = selectedProxy?.urlTestDelay ?: 0
    val urlTestProgress = proxySet.urlTestProgress

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
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

            Row {
                if (proxySet.isAll) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp),
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
                                text = proxySet.displayType,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                            Text(
                                text = proxySet.tag,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val showGroupTest = !(proxySet.isAll && isRemote)
                            if (showGroupTest) {
                                Surface(
                                    onClick = { urlTestForGroup(proxySet.id) },
                                    enabled = !proxySet.isTesting,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    if (urlTestProgress != null) {
                                        Text(
                                            text = "${urlTestProgress.current} / ${urlTestProgress.total}",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.bolt),
                                            contentDescription = stringResource(Res.string.connection_test),
                                            modifier = Modifier.padding(8.dp).size(20.dp),
                                        )
                                    }
                                }
                            }
                            SimpleIconButton(
                                imageVector = vectorResource(
                                    if (expanded) {
                                        Res.drawable.expand_less
                                    } else {
                                        Res.drawable.expand_more
                                    },
                                ),
                                contentDescription = stringResource(Res.string.expand),
                                onClick = {
                                    expandedByUser = !expanded
                                    if (!expandedByUser) {
                                        searchTextFieldState.clearText()
                                    }
                                },
                            )
                        }
                    }

                    if (expanded) {
                        if (expandedByUser) {
                            ProxySearchField(textFieldState = searchTextFieldState)
                        }
                        val searchQuery = searchTextFieldState.text.toString()
                        val visibleItems = remember(proxySet.items, searchQuery) {
                            filterProxyItems(proxySet.items, searchQuery)
                        }
                        if (visibleItems.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.not_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            ProxyGrid(
                                proxySet = proxySet,
                                items = visibleItems,
                                urlTestingTags = urlTestingTags,
                                selectProxy = selectProxy,
                                urlTestSingle = urlTestSingle,
                            )
                        }
                    } else if (!proxySet.isAll) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier.platformCombinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        selectedProxyMenuExpanded = true
                                    },
                                ),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                    URLTestDelayText(
                                        delay = selectedDelay,
                                        isTesting = urlTestingTags.containsKey(proxySet.selected),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = selectedProxyMenuExpanded,
                                onDismissRequest = { selectedProxyMenuExpanded = false },
                                containerColor = MenuDefaults.groupStandardContainerColor,
                                shape = MenuDefaults.standaloneGroupShape,
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.connection_test)) },
                                    onClick = {
                                        selectedProxyMenuExpanded = false
                                        urlTestSingle(proxySet.selected)
                                    },
                                    shape = MenuDefaults.itemShape(0, 1).shape,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PROXY_COLUMNS = 2
private const val PROXY_CARD_GAP = 8

@Composable
private fun ProxySearchField(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
) {
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(Res.string.search_go)) },
        leadingIcon = {
            Icon(
                imageVector = vectorResource(Res.drawable.search),
                contentDescription = stringResource(Res.string.search_go),
            )
        },
        trailingIcon = if (textFieldState.text.isEmpty()) {
            null
        } else {
            {
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.close),
                    contentDescription = stringResource(Res.string.clear_search),
                    onClick = { textFieldState.clearText() },
                )
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ProxyGrid(
    modifier: Modifier = Modifier,
    proxySet: ProxySet,
    items: List<ProxyItem>,
    urlTestingTags: Map<String, Int>,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestSingle: (tag: String) -> Unit,
) {
    Grid(
        config = {
            gap(PROXY_CARD_GAP.toDp())
            if (constraints.hasBoundedWidth) {
                val gaps = PROXY_CARD_GAP * (PROXY_COLUMNS - 1)
                val columnWidth = ((constraints.maxWidth - gaps) / PROXY_COLUMNS)
                    .fastCoerceAtLeast(0)
                    .toDp()
                repeat(PROXY_COLUMNS) { column(columnWidth) }
            } else {
                repeat(PROXY_COLUMNS) { column(1.fr) }
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        items.forEach { proxy ->
            ProxyCard(
                proxy = proxy,
                selected = proxySet.selected == proxy.tag,
                isTesting = urlTestingTags.containsKey(proxy.tag),
                selectable = proxySet.selectable,
                select = { selectProxy(proxySet.tag, proxy.tag) },
                urlTest = { urlTestSingle(proxy.tag) },
            )
        }
    }
}

@Composable
private fun ProxyCard(
    modifier: Modifier = Modifier,
    proxy: ProxyItem,
    selected: Boolean,
    isTesting: Boolean,
    selectable: Boolean,
    select: () -> Unit,
    urlTest: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .platformCombinedClickable(
                    onClick = {
                        if (selectable) {
                            select()
                        }
                    },
                    onLongClick = { menuExpanded = true },
                ),
            shape = RoundedCornerShape(14.dp),
            color = containerColor,
            border = BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = proxy.tag,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = proxy.displayType,
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.labelMedium,
                )
                URLTestDelayText(
                    delay = proxy.urlTestDelay,
                    isTesting = isTesting,
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = MenuDefaults.groupStandardContainerColor,
            shape = MenuDefaults.standaloneGroupShape,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.connection_test)) },
                onClick = {
                    menuExpanded = false
                    urlTest()
                },
                shape = MenuDefaults.itemShape(0, 1).shape,
            )
        }
    }
}

@Composable
private fun URLTestDelayText(
    modifier: Modifier = Modifier,
    delay: Int,
    isTesting: Boolean = false,
) {
    if (isTesting) {
        CircularProgressIndicator(
            modifier = modifier.size(15.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
        )
        return
    }
    Text(
        text = if (delay > 0) delay.toString() else "--",
        color = if (delay > 0) colorForUrlTestDelay(delay) else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Preview
@Composable
private fun PreviewProxySet() {
    val uiState = remember {
        DashboardState(
            proxySets = listOf(
                ProxySet(
                    tag = "♻️自动选择",
                    displayType = "URLTest",
                    selected = "🇭🇰 Hong Kong",
                    items = listOf(
                        ProxyItem("🇭🇰 Hong Kong", "Shadowsocks", 18),
                        ProxyItem(
                            "Long long Advertisement -- example.com -- Expire: 2099-12-31 -- Invite your friend plz",
                            "VLESS",
                        ),
                        ProxyItem("🇺🇸 US - LAX", "Hysteria2", 140),
                        ProxyItem("🇩🇪 Germany - Frankfurt", "VMess", 888),
                        ProxyItem("🇦🇶 Antarctica", "Snell", 1762),
                    ),
                ),
            ),
            urlTestingTags = mapOf("🇺🇸 US - LAX" to 1),
        )
    }
    DashboardProxySetScreen(
        uiState = uiState,
        contentPadding = PaddingValues(bottom = 64.dp),
        searchTextFieldState = rememberTextFieldState(),
        setSearchMode = {},
        selectProxy = { _, _ -> },
        urlTestForSingle = {},
        urlTestForGroup = {},
    )
}
