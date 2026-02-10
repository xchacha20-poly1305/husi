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
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.Key
import fr.husi.RuleProvider
import fr.husi.compose.MoreOverIcon
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.showAndDismissOld
import fr.husi.libcore.Libcore
import fr.husi.repository.repo
import fr.husi.resources.*
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import java.io.File

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
    onOpenAssetEditor: (String, (AssetEditResult) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetsScreenViewModel = viewModel { AssetsScreenViewModel() },
) {
    val cacheDir = repo.cacheDir
    val assetsDir = repo.externalAssetsDir
    val geoDir = remember { geoDir(assetsDir) }
    val scope = rememberCoroutineScope()
    val rulesProvider by DataStore.configurationStore
        .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
        .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)

    LaunchedEffect(viewModel, assetsDir, geoDir) {
        viewModel.initialize(
            assetsDir = assetsDir,
            geoDir = geoDir,
        )
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

    LaunchedEffect(uiState.pendingDeleteCount) {
        if (uiState.pendingDeleteCount > 0) {
            val result = snackbarHostState.showAndDismissOld(
                message = repo.getPluralString(
                    Res.plurals.removed,
                    uiState.pendingDeleteCount,
                    uiState.pendingDeleteCount,
                ),
                actionLabel = repo.getString(Res.string.undo),
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
                        actionLabel = repo.getString(Res.string.ok),
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
                TopAppBar(
                    title = { Text(stringResource(Res.string.route_assets)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.arrow_back),
                            contentDescription = stringResource(Res.string.back),
                            onClick = onBackPress,
                        )
                    },
                    actions = {
                        AppBarRow(
                            overflowIndicator = ::MoreOverIcon,
                            maxItemCount = 2,
                        ) {
                            val canOperate =
                                uiState.process == null && uiState.assets.all { it.progress == null }
                            val canReset = canOperate && rulesProvider == RuleProvider.OFFICIAL
                            clickableItem(
                                onClick = {
                                    viewModel.updateAsset(
                                        destinationDir = geoDir,
                                        cacheDir = cacheDir,
                                    )
                                },
                                icon = {
                                    Icon(vectorResource(Res.drawable.update), null)
                                },
                                label = runBlocking { repo.getString(Res.string.assets_update) },
                                enabled = canOperate,
                            )
                            clickableItem(
                                onClick = {
                                    viewModel.resetRuleSet()
                                },
                                icon = {
                                    Icon(vectorResource(Res.drawable.replay), null)
                                },
                                label = runBlocking { repo.getString(Res.string.reset_rule_set) },
                                enabled = canReset,
                            )
                            clickableItem(
                                onClick = {
                                    importFile.launch()
                                },
                                icon = {
                                    Icon(vectorResource(Res.drawable.note_add), null)
                                },
                                label = runBlocking { repo.getString(Res.string.action_import_file) },
                            )
                            clickableItem(
                                onClick = {
                                    onOpenAssetEditor("", ::handleAssetEditResult)
                                },
                                icon = {
                                    Icon(vectorResource(Res.drawable.link), null)
                                },
                                label = runBlocking { repo.getString(Res.string.import_url) },
                            )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding.withNavigation(),
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
                            viewModel = viewModel,
                            uiState = uiState,
                            onEditAsset = { name ->
                                onOpenAssetEditor(name, ::handleAssetEditResult)
                            },
                        )
                    }
                } else {
                    AssetCard(
                        asset = asset,
                        viewModel = viewModel,
                        uiState = uiState,
                        onEditAsset = { name ->
                            onOpenAssetEditor(name, ::handleAssetEditResult)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetCard(
    asset: AssetItem,
    viewModel: AssetsScreenViewModel,
    uiState: AssetsUiState,
    onEditAsset: (String) -> Unit,
) {
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
