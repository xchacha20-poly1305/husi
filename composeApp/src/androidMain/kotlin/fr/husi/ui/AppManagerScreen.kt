@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.extraBottomPadding
import fr.husi.compose.paddingExceptBottom
import fr.husi.compose.setPlainText
import fr.husi.repository.FakeRepository
import fr.husi.repository.repo
import fr.husi.utils.PackageCache
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import kotlinx.coroutines.launch
import fr.husi.resources.*

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal actual fun AppManagerScreen(
    onBackPress: () -> Unit,
    modifier: Modifier,
) {
    val viewModel: AppManagerViewModel = viewModel { AppManagerViewModel() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.initialize(context.packageManager)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.shouldFinish) {
        if (uiState.shouldFinish) onBackPress()
    }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = getStringOrRes(message),
                actionLabel = repo.getString(Res.string.ok),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val searchBarState = rememberSearchBarState()
    val textFieldState = viewModel.textFieldState

    var showMoreActions by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listScrollState = rememberLazyListState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val colors = TopAppBarDefaults.topAppBarColors()
            val isScrolled = scrollBehavior.state.overlappedFraction > 0
            val containerColor = if (isScrolled) {
                colors.scrolledContainerColor
            } else {
                colors.containerColor
            }

            Surface(
                color = containerColor,
            ) {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.proxied_apps)) },
                        navigationIcon = {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.close),
                                contentDescription = stringResource(Res.string.close),
                                onClick = onBackPress,
                            )
                        },
                        actions = {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.copy_all),
                                contentDescription = stringResource(Res.string.action_copy),
                                onClick = {
                                    val toExport = viewModel.export()
                                    scope.launch {
                                        clipboard.setPlainText(toExport)
                                        snackbarHostState.showSnackbar(
                                            message = repo.getString(Res.string.copy_success),
                                            actionLabel = repo.getString(Res.string.ok),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                            )
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.content_paste),
                                contentDescription = stringResource(Res.string.action_import),
                                onClick = {
                                    scope.launch {
                                        val text = clipboard.getClipEntry()?.clipData
                                            ?.getItemAt(0)?.text
                                            ?.toString()
                                        viewModel.import(text)
                                    }
                                },
                            )
                            Box {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.more_vert),
                                    contentDescription = stringResource(Res.string.more),
                                    onClick = { showMoreActions = true },
                                )

                                DropdownMenu(
                                    expanded = showMoreActions,
                                    onDismissRequest = { showMoreActions = false },
                                    shape = MenuDefaults.standaloneGroupShape,
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.action_scan_china_apps)) },
                                        onClick = {
                                            viewModel.scanChinaApps()
                                            showMoreActions = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = vectorResource(Res.drawable.document_scanner),
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.invert_selections)) },
                                        onClick = {
                                            viewModel.invertSections()
                                            showMoreActions = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = vectorResource(Res.drawable.compare_arrows),
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.clear_selections)) },
                                        onClick = {
                                            viewModel.clearSections()
                                            showMoreActions = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = vectorResource(Res.drawable.cleaning_services),
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                }
                            }
                        },
                        windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                        scrollBehavior = scrollBehavior,
                    )

                    SearchBar(
                        state = searchBarState,
                        inputField = {
                            SearchBarDefaults.InputField(
                                textFieldState = textFieldState,
                                searchBarState = searchBarState,
                                onSearch = {
                                    scope.launch {
                                        searchBarState.animateToCollapsed()
                                    }
                                },
                                leadingIcon = {
                                    Icon(vectorResource(Res.drawable.search), null)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ProxyModeSelector(
                        selectedMode = uiState.mode,
                        onSelect = { mode ->
                            viewModel.setProxyMode(mode)
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 300),
        ) { isLoading ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                AppList(
                    uiState = uiState,
                    scrollState = listScrollState,
                    innerPadding = innerPadding,
                    onClick = { viewModel.onItemClick(it) },
                )
            }
        }
    }

    val scanned = uiState.scanned
    if (scanned != null) {
        ScanDialog(
            scanned = scanned,
            progress = uiState.scanProcess,
            onCancel = { viewModel.cancelScan() },
        )
    }
}

@Composable
private fun ProxyModeSelector(
    selectedMode: ProxyMode,
    onSelect: (ProxyMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        val isDisabled = selectedMode == ProxyMode.DISABLED
        SegmentedButton(
            selected = isDisabled,
            onClick = { onSelect(ProxyMode.DISABLED) },
            shape = SegmentedButtonDefaults.itemShape(0, 3),
            icon = {
                SegmentedButtonDefaults.Icon(
                    active = isDisabled,
                    activeContent = {
                        Icon(vectorResource(Res.drawable.question_mark), null)
                    },
                    inactiveContent = {
                        Icon(vectorResource(Res.drawable.close), null)
                    },
                )
            },
            label = { Text(stringResource(Res.string.off)) },
        )
        val isProxy = selectedMode == ProxyMode.PROXY
        SegmentedButton(
            selected = isProxy,
            onClick = { onSelect(ProxyMode.PROXY) },
            shape = SegmentedButtonDefaults.itemShape(1, 3),
            icon = {
                SegmentedButtonDefaults.Icon(
                    active = isProxy,
                    inactiveContent = {
                        Icon(vectorResource(Res.drawable.flight_takeoff), null)
                    },
                )
            },
            label = { Text(stringResource(Res.string.route_proxy)) },
        )
        val isBypass = selectedMode == ProxyMode.BYPASS
        SegmentedButton(
            selected = isBypass,
            onClick = { onSelect(ProxyMode.BYPASS) },
            shape = SegmentedButtonDefaults.itemShape(2, 3),
            icon = {
                SegmentedButtonDefaults.Icon(
                    active = isBypass,
                    inactiveContent = {
                        Icon(vectorResource(Res.drawable.shuffle), null)
                    },
                )
            },
            label = { Text(stringResource(Res.string.bypass_apps)) },
        )
    }
}

@Composable
private fun AppList(
    uiState: AppManagerUiState,
    scrollState: LazyListState,
    innerPadding: PaddingValues,
    onClick: (ProxiedApp) -> Unit,
) {
    Row(
        modifier = Modifier.paddingExceptBottom(innerPadding),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            state = scrollState,
            contentPadding = extraBottomPadding(),
        ) {
            items(
                items = uiState.apps,
                key = { it.packageName },
                contentType = { 0 },
            ) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = rememberDrawablePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 12.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .width(0.dp), // Make sure it works in Row with weight
                        ) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${app.packageName} (${app.uid})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = app.isProxied,
                            onCheckedChange = { onClick(app) },
                        )
                    }
                }
            }
        }

        Box {
            VerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = scrollState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 16.dp,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanDialog(
    scanned: List<String>,
    progress: Float?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val listState = rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(scanned.size) }

    LaunchedEffect(scanned.size) {
        if (scanned.isNotEmpty() && scanned.size >= previousCount) {
            listState.scrollToItem(scanned.size - 1)
        }
        previousCount = scanned.size
    }

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.cancel))
            }
        },
        title = { Text(stringResource(Res.string.action_scan_china_apps)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val indicatorModifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = indicatorModifier,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = indicatorModifier,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = listState,
                ) {
                    items(
                        items = scanned,
                        key = { it },
                        contentType = { 0 },
                    ) { item ->
                        val label = PackageCache.loadLabel(packageManager, item)
                        Text(
                            text = "$label ($item)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewAppManagerScreen() {
    LaunchedEffect(Unit) {
        repo = FakeRepository()
    }

    AppManagerScreen(
        onBackPress = {},
    )
}
