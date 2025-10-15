package io.nekohasekai.sagernet.ui

import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ktx.first
import io.nekohasekai.sagernet.ktx.trySetPrimaryClip
import io.nekohasekai.sagernet.repository.TempRepository
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.launch

class AppManagerActivity : ComposeActivity() {

    private val viewModel by viewModels<AppManagerActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            PackageCache.awaitLoadSync()
        }

        setContent {
            AppTheme {
                AppManagerScreen(
                    viewModel = viewModel,
                    onBackPress = { onBackPressedDispatcher.onBackPressed() },
                    finish = ::finish,
                )
            }
        }

        if (savedInstanceState == null) {
            viewModel.initialize(packageManager)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: AppManagerActivityViewModel,
    onBackPress: () -> Unit,
    finish: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is AppManagerUiEvent.Snackbar -> {
                    val message = context.getStringOrRes(event.message)
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                AppManagerUiEvent.Finish -> finish()
            }
        }
    }

    var searchActivate by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    val clipboardManager = remember(context) { context.getSystemService<ClipboardManager>() }

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
                        title = {
                            if (searchActivate) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text(stringResource(android.R.string.search_go)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.5f
                                        ),
                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.2f
                                        ),
                                    ),
                                )
                            } else {
                                Text(stringResource(R.string.proxied_apps))
                            }
                        },
                        navigationIcon = {
                            SimpleIconButton(
                                imageVector = Icons.Filled.Close,
                                onClick = onBackPress,
                            )
                        },
                        actions = {
                            if (searchActivate) {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.SearchOff,
                                    contentDescription = stringResource(R.string.close),
                                ) {
                                    searchActivate = false
                                    viewModel.setSearchQuery("")
                                }
                            } else {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(android.R.string.search_go),
                                    onClick = { searchActivate = true },
                                )

                                SimpleIconButton(
                                    imageVector = Icons.Filled.CopyAll,
                                    contentDescription = stringResource(R.string.action_copy),
                                ) {
                                    val toExport = viewModel.export()
                                    val success = clipboardManager?.trySetPrimaryClip(toExport) ?: false
                                    scope.launch {
                                        val message = if (success) {
                                            context.getString(R.string.copy_success)
                                        } else {
                                            context.getString(R.string.copy_failed)
                                        }
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = context.getString(android.R.string.ok),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                                SimpleIconButton(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = stringResource(R.string.action_import),
                                ) {
                                    val text = clipboardManager?.first()
                                    viewModel.import(text)
                                }

                                Box {
                                    SimpleIconButton(Icons.Filled.MoreVert) {
                                        showOverflowMenu = true
                                    }
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_scan_china_apps)) },
                                            onClick = {
                                                showOverflowMenu = false
                                                viewModel.scanChinaApps()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.invert_selections)) },
                                            onClick = {
                                                viewModel.invertSections()
                                                showOverflowMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.clear_selections)) },
                                            onClick = {
                                                viewModel.clearSections()
                                                showOverflowMenu = false
                                            },
                                        )
                                    }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            Crossfade(
                targetState = uiState.isLoading,
                animationSpec = tween(durationMillis = 300),
            ) { isLoading ->
                if (isLoading) {
                    Box(Modifier.fillMaxSize()) {
                        LoadingIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                } else {
                    AppList(
                        uiState = uiState,
                        scrollState = listScrollState,
                        onClick = { viewModel.onItemClick(it) },
                    )
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        FilterChip(
            selected = selectedMode == ProxyMode.DISABLED,
            onClick = { onSelect(ProxyMode.DISABLED) },
            label = {
                Text(
                    text = stringResource(R.string.off),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selectedMode == ProxyMode.PROXY,
            onClick = { onSelect(ProxyMode.PROXY) },
            label = {
                Text(
                    text = stringResource(R.string.route_proxy),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selectedMode == ProxyMode.BYPASS,
            onClick = { onSelect(ProxyMode.BYPASS) },
            label = {
                Text(
                    text = stringResource(R.string.bypass_apps),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AppList(
    uiState: AppManagerUiState,
    scrollState: LazyListState,
    onClick: (ProxiedApp) -> Unit,
) {
    Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
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

        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = scrollState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 16.dp,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanDialog(
    scanned: List<String>,
    progress: Int?,
    onCancel: () -> Unit,
) {
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
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.action_scan_china_apps)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                if (progress != null) {
                    LoadingIndicator()
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(maxHeight = 240.dp),
                    state = listState,
                ) {
                    items(
                        items = scanned,
                        key = { it },
                        contentType = { 0 },
                    ) { item ->
                        Text(
                            text = item,
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
    val context = LocalContext.current
    repo = TempRepository(context)

    AppManagerScreen(
        viewModel = AppManagerActivityViewModel(),
        onBackPress = {},
        finish = {},
    )
}
