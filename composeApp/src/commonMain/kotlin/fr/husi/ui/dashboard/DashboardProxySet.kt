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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
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
import fr.husi.resources.close
import fr.husi.resources.connection_test
import fr.husi.resources.expand
import fr.husi.resources.expand_less
import fr.husi.resources.expand_more
import fr.husi.resources.no_results
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
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestForSingle: (tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
    groupSearchTextFieldState: TextFieldState,
    openGroupSearch: (group: String) -> Unit,
    closeGroupSearch: () -> Unit,
) {
    val query = uiState.proxySetQuery
    val listState = rememberLazyListState()

    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = uiState.proxySets,
                key = { it.id },
                contentType = { 0 },
            ) { proxySet ->
                ProxySetCard(
                    proxySet = proxySet,
                    isRemote = uiState.isRemote,
                    urlTestingTags = uiState.urlTestingTags,
                    selectProxy = selectProxy,
                    urlTestSingle = urlTestForSingle,
                    urlTestForGroup = urlTestForGroup,
                    isSearching = query.searchingProxyGroup == proxySet.id,
                    isForceExpanded = query.forcesExpanded(proxySet),
                    showGroupSearchButton = !query.hasGlobalQuery,
                    groupSearchTextFieldState = groupSearchTextFieldState,
                    openGroupSearch = openGroupSearch,
                    closeGroupSearch = closeGroupSearch,
                )
            }
        }

        BoxedVerticalScrollbar(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxHeight(),
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
    isRemote: Boolean,
    urlTestingTags: Map<String, Int>,
    selectProxy: (group: String, tag: String) -> Unit,
    urlTestSingle: (tag: String) -> Unit,
    urlTestForGroup: (group: String) -> Unit,
    isSearching: Boolean,
    isForceExpanded: Boolean,
    showGroupSearchButton: Boolean,
    groupSearchTextFieldState: TextFieldState,
    openGroupSearch: (group: String) -> Unit,
    closeGroupSearch: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedProxyMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val shownExpanded = expanded || isForceExpanded
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
                            if (shownExpanded && showGroupSearchButton) {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.search),
                                    contentDescription = stringResource(Res.string.search_go),
                                    onClick = { openGroupSearch(proxySet.id) },
                                )
                            }
                            SimpleIconButton(
                                imageVector = vectorResource(
                                    if (shownExpanded) {
                                        Res.drawable.expand_less
                                    } else {
                                        Res.drawable.expand_more
                                    },
                                ),
                                contentDescription = stringResource(Res.string.expand),
                                onClick = {
                                    if (isSearching) closeGroupSearch()
                                    expanded = !shownExpanded
                                },
                            )
                        }
                    }

                    if (isSearching) {
                        ProxyGroupSearchField(
                            state = groupSearchTextFieldState,
                            onClose = closeGroupSearch,
                        )
                    }

                    if (shownExpanded) {
                        if (proxySet.items.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.no_results),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            ProxyGrid(
                                proxySet = proxySet,
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

@Composable
private fun ProxyGroupSearchField(
    state: TextFieldState,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    OutlinedTextField(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        placeholder = { Text(stringResource(Res.string.search_go)) },
        trailingIcon = {
            SimpleIconButton(
                imageVector = vectorResource(Res.drawable.close),
                contentDescription = stringResource(Res.string.close),
                onClick = onClose,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { focusManager.clearFocus() },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

private const val PROXY_COLUMNS = 2
private const val PROXY_CARD_GAP = 8

@Composable
private fun ProxyGrid(
    modifier: Modifier = Modifier,
    proxySet: ProxySet,
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
        proxySet.items.forEach { proxy ->
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

private fun previewProxySets() = listOf(
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
)

@Preview
@Composable
private fun PreviewProxySet() {
    val uiState = remember {
        DashboardState(
            proxySets = previewProxySets(),
            urlTestingTags = mapOf("🇺🇸 US - LAX" to 1),
        )
    }
    DashboardProxySetScreen(
        uiState = uiState,
        contentPadding = PaddingValues(bottom = 64.dp),
        selectProxy = { _, _ -> },
        urlTestForSingle = {},
        urlTestForGroup = {},
        groupSearchTextFieldState = remember { TextFieldState() },
        openGroupSearch = {},
        closeGroupSearch = {},
    )
}

@Preview
@Composable
private fun PreviewProxySetSearching() {
    val uiState = remember {
        DashboardState(
            proxySets = previewProxySets(),
            proxySetQuery = ProxySetQuery(searchingProxyGroup = "♻️自动选择", groupSearch = "Hong"),
            urlTestingTags = mapOf("🇺🇸 US - LAX" to 1),
        )
    }
    val searchState = remember { TextFieldState("Hong") }
    DashboardProxySetScreen(
        uiState = uiState,
        contentPadding = PaddingValues(bottom = 64.dp),
        selectProxy = { _, _ -> },
        urlTestForSingle = {},
        urlTestForGroup = {},
        groupSearchTextFieldState = searchState,
        openGroupSearch = {},
        closeGroupSearch = {},
    )
}
