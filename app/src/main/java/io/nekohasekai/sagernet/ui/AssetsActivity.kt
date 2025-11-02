package io.nekohasekai.sagernet.ui

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.ComposeSnackbarAdapter
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.paddingWithNavigation
import io.nekohasekai.sagernet.compose.startFilesForResult
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.launch
import java.io.File

@ExperimentalMaterial3Api
class AssetsActivity : ComposeActivity() {

    private val viewModel: AssetsActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetsDir = assetsDir()
        viewModel.initialize(
            assetsDir = assetsDir,
            geoDir = geoDir(assetsDir),
        )
        setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                AssetsScreen(
                    viewModel = viewModel,
                    onBackPress = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }

    }

}

private const val ASSET_BUILT_IN = 0
private const val ASSET_CUSTOM = 1

private fun Context.assetsDir(): File {
    return (getExternalFilesDir(null) ?: filesDir).apply {
        mkdirs()
    }
}

private fun geoDir(assetsDir: File): File {
    return File(assetsDir, "geo").apply {
        mkdirs()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssetsScreen(
    modifier: Modifier = Modifier,
    viewModel: AssetsActivityViewModel,
    onBackPress: () -> Unit,
) {
    val context = LocalContext.current
    val cacheDir = remember(context) { context.cacheDir }
    val assetsDir = remember(context) { context.assetsDir() }
    val geoDir = remember(context) { geoDir(assetsDir) }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        viewModel.importFile(context.contentResolver, uri, cacheDir, geoDir)
    }
    val importUrl = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val assetName = result.data?.getStringExtra(AssetEditActivity.EXTRA_ASSET_NAME)

        when (result.resultCode) {
            RESULT_OK -> {}

            AssetEditActivity.RESULT_CREATED -> {
                // Callback will auto-update when asset appears in DB
            }

            AssetEditActivity.RESULT_SHOULD_UPDATE -> runOnDefaultDispatcher {
                viewModel.updateSingleAsset(File(geoDir, assetName!!))
            }

            AssetEditActivity.RESULT_DELETE -> runOnDefaultDispatcher {
                viewModel.deleteAssets(listOf(File(geoDir, assetName!!)))
            }
        }
    }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoManager = remember {
        UndoSnackbarManager(
            snackbar = ComposeSnackbarAdapter(
                showSnackbar = { message, label ->
                    snackbarHostState.showSnackbar(
                        message = context.getStringOrRes(message),
                        actionLabel = context.getStringOrRes(label),
                        duration = SnackbarDuration.Short,
                    )
                },
                scope = scope,
            ),
            callback = viewModel,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            undoManager.flush()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            var showImportMenu by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TopAppBar(
                    title = { Text(stringResource(R.string.route_assets)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back),
                            onClick = onBackPress,
                        )
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.update),
                            contentDescription = stringResource(R.string.assets_update),
                            enabled = uiState.process == null && uiState.assets.all { it.progress == null },
                            onClick = {
                                viewModel.updateAsset(
                                    destinationDir = geoDir,
                                    cacheDir = cacheDir,
                                )
                            },
                        )
                        Box {
                            SimpleIconButton(
                                imageVector = ImageVector.vectorResource(R.drawable.note_add),
                                contentDescription = stringResource(R.string.import_asset),
                                onClick = { showImportMenu = true },
                            )
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_import_file)) },
                                    onClick = {
                                        showImportMenu = false
                                        startFilesForResult(importFile, "*/*") { id ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = context.getString(id),
                                                    actionLabel = context.getString(android.R.string.ok),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_url)) },
                                    onClick = {
                                        showImportMenu = false
                                        importUrl.launch(
                                            Intent(context, AssetEditActivity::class.java)
                                        )
                                    }
                                )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding.paddingWithNavigation(),
        ) {
            itemsIndexed(
                items = uiState.assets,
                key = { index, asset -> asset.file.name },
                contentType = { index, asset ->
                    if (asset.builtIn) {
                        ASSET_BUILT_IN
                    } else {
                        ASSET_CUSTOM
                    }
                },
            ) { index, asset ->
                val swipeState = rememberSwipeToDismissBoxState()

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
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(ImageVector.vectorResource(R.drawable.delete), null)
                            }
                        },
                        onDismiss = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd,
                                SwipeToDismissBoxValue.EndToStart,
                                    -> {
                                    viewModel.fakeRemove(index)
                                    undoManager.remove(index to asset)
                                }

                                else -> {}
                            }
                        },
                    ) {
                        AssetCard(
                            asset = asset,
                            importUrl = importUrl,
                            viewModel = viewModel,
                            uiState = uiState,
                        )
                    }
                } else {
                    AssetCard(
                        asset = asset,
                        importUrl = importUrl,
                        viewModel = viewModel,
                        uiState = uiState,
                    )
                }

            }
        }
    }
}

@Composable
private fun AssetCard(
    asset: AssetItem,
    importUrl: ActivityResultLauncher<Intent>,
    viewModel: AssetsActivityViewModel,
    uiState: AssetsUiState,
) {
    val context = LocalContext.current

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                            R.string.route_asset_status,
                            asset.version
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
                                    imageVector = ImageVector.vectorResource(R.drawable.edit),
                                    contentDescription = stringResource(R.string.edit),
                                    enabled = clickable,
                                    onClick = {
                                        importUrl.launch(
                                            Intent(context, AssetEditActivity::class.java)
                                                .putExtra(
                                                    AssetEditActivity.EXTRA_ASSET_NAME,
                                                    asset.file.name
                                                )
                                        )
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
                            Text(stringResource(R.string.group_update))
                        }
                    }
                }
            }
        }
    }
}