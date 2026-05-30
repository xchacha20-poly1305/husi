package fr.husi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import fr.husi.compose.SwipeableSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.Key
import fr.husi.RuleProvider
import fr.husi.bg.RouteAssetUpdater
import fr.husi.bg.currentEpochSeconds
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.showAndDismissOld
import fr.husi.libcore.Libcore
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_import_file
import fr.husi.resources.arrow_back
import fr.husi.resources.assets_update
import fr.husi.resources.back
import fr.husi.resources.cancel
import fr.husi.resources.delete
import fr.husi.resources.edit
import fr.husi.resources.group_update
import fr.husi.resources.import_url
import fr.husi.resources.link
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.note_add
import fr.husi.resources.ok
import fr.husi.resources.removed
import fr.husi.resources.replay
import fr.husi.resources.reset_rule_set
import fr.husi.resources.route_asset_auto_update_off
import fr.husi.resources.route_asset_auto_update_on
import fr.husi.resources.route_asset_status
import fr.husi.resources.route_assets
import fr.husi.resources.route_global_asset_auto_update_delay
import fr.husi.resources.timer
import fr.husi.resources.undo
import fr.husi.resources.update
import fr.husi.results.ResultEffect
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import java.io.File
import kotlin.random.Random

private const val ASSET_BUILT_IN = 0
private const val ASSET_CUSTOM = 1

private fun geoDir(assetsDir: File): File {
    return File(assetsDir, "geo").apply {
        mkdirs()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AssetsScreen(
    onBackPress: () -> Unit,
    onOpenAssetEditor: (NavRoutes.AssetEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cacheDir = resolveRepository().cacheDir
    val assetsDir = resolveRepository().externalAssetsDir
    val geoDir = remember { geoDir(assetsDir) }
    val viewModel: AssetsScreenViewModel = viewModel { AssetsScreenViewModel(assetsDir, geoDir) }
    val scope = rememberCoroutineScope()
    val activeResultKeys = remember { mutableStateListOf<String>() }
    val rulesProvider by DataStore.configurationStore
        .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
        .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)
    val routeAssetsAutoUpdateDelay by DataStore.configurationStore
        .intFlow(Key.ROUTE_ASSETS_AUTO_UPDATE_DELAY, 0)
        .collectAsStateWithLifecycle(0)
    var showAutoUpdateDelayDialog by remember { mutableStateOf(false) }
    var autoUpdateDelayValue by remember(routeAssetsAutoUpdateDelay, showAutoUpdateDelayDialog) {
        mutableStateOf(TextFieldValue(routeAssetsAutoUpdateDelay.toString()))
    }
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }

    fun saveRouteAssetsAutoUpdateDelay() {
        val delay = autoUpdateDelayValue.text.toIntOrNull() ?: 0
        showAutoUpdateDelayDialog = false
        scope.launch(Dispatchers.Default) {
            DataStore.routeAssetsAutoUpdateDelay = delay
            RouteAssetUpdater.reconfigureUpdater()
        }
    }

    fun handleAssetEditResult(result: AssetEditResult) {
        when (result) {
            is AssetEditResult.ShouldUpdate -> {
                viewModel.updateSingleAsset(File(geoDir, result.assetName))
            }

            is AssetEditResult.Deleted -> {
                scope.launch(Dispatchers.IO) {
                    viewModel.deleteAssets(listOf(File(geoDir, result.assetName)))
                }
            }

            else -> {}
        }
    }

    fun openAssetEditor(assetName: String) {
        val resultKey = assetName.ifEmpty {
            "asset-edit-new-${Random.nextLong()}"
        }
        if (resultKey !in activeResultKeys) {
            activeResultKeys += resultKey
        }
        onOpenAssetEditor(
            NavRoutes.AssetEdit(
                assetName = assetName,
                resultKey = resultKey,
            ),
        )
    }

    val importFile = rememberFilePickerLauncher { file ->
        scope.launch(Dispatchers.IO) {
            if (file == null) return@launch
            val fileName = file.name

            val tempImportFile = File(cacheDir, fileName).apply {
                parentFile?.mkdirs()
            }
            try {
                tempImportFile.writeBytes(file.readBytes())
            } catch (e: Exception) {
                Logs.e(e)
                return@launch
            }
            try {
                Libcore.tryUnpack(tempImportFile.absolutePath, geoDir.absolutePath)
            } catch (e: Exception) {
                Logs.e(e)
                return@launch
            } finally {
                tempImportFile.delete()
            }

            val nameList = listOf("geosite", "geoip")
            for (name in nameList) {
                val file = File(assetsDir, "$name.version.txt")
                if (file.isFile) file.delete()
                file.createNewFile()
                file.writeText("Custom")
            }

            DataStore.routeAssetsLastUpdated = currentEpochSeconds()
            RouteAssetUpdater.reconfigureUpdater()
            viewModel.refreshAssets()
        }
    }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val snackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.commit()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    for (resultKey in activeResultKeys.toList()) {
        ResultEffect<AssetEditResult>(resultKey = resultKey) { result ->
            handleAssetEditResult(result)
        }
    }

    LaunchedEffect(uiState.pendingDeleteCount) {
        if (uiState.pendingDeleteCount > 0) {
            val result = snackbarHostState.showAndDismissOld(
                message = resolveRepository().getPluralString(
                    Res.plurals.removed,
                    uiState.pendingDeleteCount,
                    uiState.pendingDeleteCount,
                ),
                actionLabel = resolveRepository().getString(Res.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is AssetsScreenUiEvent.Snackbar -> scope.launch {
                    snackbarHostState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = resolveRepository().getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                CapsuleTopBar(
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.arrow_back),
                            contentDescription = stringResource(Res.string.back),
                            onClick = onBackPress,
                        )
                    },
                    title = { Text(stringResource(Res.string.route_assets)) },
                    actions = {
                        val canOperate =
                            uiState.process == null && uiState.assets.all { it.progress == null }
                        val canReset = canOperate && rulesProvider == RuleProvider.OFFICIAL

                        CapsuleActionButton {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.timer),
                                contentDescription = stringResource(Res.string.route_global_asset_auto_update_delay),
                                onClick = { showAutoUpdateDelayDialog = true },
                            )
                        }
                        CapsuleActionButton {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.update),
                                contentDescription = stringResource(Res.string.assets_update),
                                enabled = canOperate,
                                onClick = {
                                    viewModel.updateAsset(cacheDir = cacheDir)
                                },
                            )
                        }
                        CapsuleActionButton {
                            Box {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.more_vert),
                                    contentDescription = stringResource(Res.string.more),
                                    onClick = { isOverflowMenuExpanded = true },
                                )

                                DropdownMenuPopup(
                                    expanded = isOverflowMenuExpanded,
                                    onDismissRequest = { isOverflowMenuExpanded = false },
                                ) {
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShape(0, 1),
                                    ) {
                                        DropdownMenuItem(
                                            selected = false,
                                            text = { Text(stringResource(Res.string.reset_rule_set)) },
                                            onClick = {
                                                isOverflowMenuExpanded = false
                                                viewModel.resetRuleSet()
                                            },
                                            leadingIcon = {
                                                Icon(vectorResource(Res.drawable.replay), null)
                                            },
                                            enabled = canReset,
                                            shapes = MenuDefaults.itemShape(0, 3),
                                        )
                                        DropdownMenuItem(
                                            selected = false,
                                            text = { Text(stringResource(Res.string.action_import_file)) },
                                            onClick = {
                                                isOverflowMenuExpanded = false
                                                importFile.launch()
                                            },
                                            leadingIcon = {
                                                Icon(vectorResource(Res.drawable.note_add), null)
                                            },
                                            shapes = MenuDefaults.itemShape(1, 3),
                                        )
                                        DropdownMenuItem(
                                            selected = false,
                                            text = { Text(stringResource(Res.string.import_url)) },
                                            onClick = {
                                                isOverflowMenuExpanded = false
                                                openAssetEditor("")
                                            },
                                            leadingIcon = {
                                                Icon(vectorResource(Res.drawable.link), null)
                                            },
                                            shapes = MenuDefaults.itemShape(2, 3),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior,
                )

                uiState.process?.let {
                    LinearWavyProgressIndicator(
                        progress = { it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                }
            }
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        val contentPadding = innerPadding.withNavigation()
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
            ) {
                items(
                    items = uiState.assets,
                    key = { asset -> asset.file.name },
                    contentType = { asset ->
                        if (asset.builtIn) {
                            ASSET_BUILT_IN
                        } else {
                            ASSET_CUSTOM
                        }
                    },
                ) { asset ->
                    val swipeState = rememberSwipeToDismissBoxState()

                    if (!asset.builtIn) {
                        LaunchedEffect(swipeState.currentValue) {
                            if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
                                viewModel.undoableRemove(asset.file.name)
                                swipeState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                    }

                    if (!asset.builtIn) {
                        SwipeToDismissBox(
                            state = swipeState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(vectorResource(Res.drawable.delete), null)
                                }
                            },
                        ) {
                            AssetCard(
                                asset = asset,
                                globalAutoUpdateDelay = routeAssetsAutoUpdateDelay,
                                viewModel = viewModel,
                                uiState = uiState,
                                onEditAsset = { openAssetEditor(it) },
                            )
                        }
                    } else {
                        AssetCard(
                            asset = asset,
                            globalAutoUpdateDelay = routeAssetsAutoUpdateDelay,
                            viewModel = viewModel,
                            uiState = uiState,
                            onEditAsset = { openAssetEditor(it) },
                        )
                    }
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

    if (showAutoUpdateDelayDialog) {
        AlertDialog(
            onDismissRequest = { showAutoUpdateDelayDialog = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    saveRouteAssetsAutoUpdateDelay()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showAutoUpdateDelayDialog = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.timer), null) },
            title = { Text(stringResource(Res.string.route_global_asset_auto_update_delay)) },
            text = {
                UIntegerTextField(
                    value = autoUpdateDelayValue,
                    onValueChange = { autoUpdateDelayValue = it },
                    onOk = ::saveRouteAssetsAutoUpdateDelay,
                )
            },
        )
    }
}

@Composable
private fun AssetCard(
    asset: AssetItem,
    globalAutoUpdateDelay: Int,
    viewModel: AssetsScreenViewModel,
    uiState: AssetsUiState,
    onEditAsset: (String) -> Unit,
) {
    val autoUpdateDelay = if (asset.builtIn) {
        globalAutoUpdateDelay
    } else {
        asset.autoUpdateDelay
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            asset.progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = asset.file.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(
                            Res.string.route_asset_status,
                            asset.version,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = routeAssetAutoUpdateSummary(autoUpdateDelay),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!asset.builtIn) {
                    Column(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val clickable = uiState.process == null && asset.progress == null
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Box(modifier = Modifier.size(36.dp)) {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.edit),
                                    contentDescription = stringResource(Res.string.edit),
                                    enabled = clickable,
                                    onClick = {
                                        onEditAsset(asset.file.name)
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.updateSingleAsset(asset.file)
                            },
                            enabled = clickable,
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 6.dp,
                            ),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                        ) {
                            Text(stringResource(Res.string.group_update))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun routeAssetAutoUpdateSummary(autoUpdateDelay: Int): String {
    return if (autoUpdateDelay <= 0) {
        stringResource(Res.string.route_asset_auto_update_off)
    } else {
        stringResource(Res.string.route_asset_auto_update_on, autoUpdateDelay)
    }
}
