package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.setPlainText
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.repository.TempRepository
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.launch

class AppListActivity : ComposeActivity() {

    companion object {
        const val EXTRA_APP_LIST = "app_list"
    }

    private val viewModel by viewModels<AppListActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PackageCache.awaitLoadSync()

        setContent {
            BackHandler(enabled = true) {
                saveAndExit()
            }

            AppTheme {
                AppListScreen(
                    viewModel = viewModel,
                    onBackPress = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }

        val packages = intent.getStringArrayListExtra(EXTRA_APP_LIST)?.toSet() ?: emptySet()
        viewModel.initialize(packageManager, packages)
    }

    private fun saveAndExit() {
        setResult(
            RESULT_OK,
            Intent()
                .putStringArrayListExtra(EXTRA_APP_LIST, viewModel.allPackages()),
        )
        finish()
    }

}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppListScreen(
    modifier: Modifier = Modifier,
    viewModel: AppListActivityViewModel,
    onBackPress: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is AppListActivityUIEvent.Snackbar -> {
                    val message = context.getStringOrRes(event.message)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = context.getString(android.R.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
        }
    }

    var searchActivate by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    var showOverflowMenu by remember { mutableStateOf(false) }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
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
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                            }),
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
                        Text(stringResource(R.string.select_apps))
                    }
                },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = Icons.Filled.Close,
                        onClick = onBackPress,
                    )
                },
                actions = {
                    if (searchActivate) SimpleIconButton(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = stringResource(R.string.close),
                    ) {
                        searchActivate = false
                        viewModel.setSearchQuery("")
                    } else {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(android.R.string.search_go),
                            onClick = { searchActivate = true },
                        )

                        val clipboard = LocalClipboard.current
                        SimpleIconButton(
                            imageVector = Icons.Filled.CopyAll,
                            contentDescription = stringResource(R.string.action_copy),
                        ) {
                            val toExport = viewModel.export()
                            scope.launch {
                                clipboard.setPlainText(toExport)
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.copy_success),
                                    actionLabel = context.getString(android.R.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                        SimpleIconButton(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = stringResource(R.string.action_import),
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
                            SimpleIconButton(Icons.Filled.MoreVert) {
                                showOverflowMenu = true
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
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
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .paddingExceptBottom(innerPadding)
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 300),
        ) { isLoading ->
            if (isLoading) {
                Box(modifier = modifier) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                List(
                    modifier = modifier,
                    uiState = uiState,
                    onClick = { viewModel.onItemClick(it) },
                )
            }
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun List(
    modifier: Modifier,
    uiState: AppListActivityUiState,
    onClick: (ProxiedApp) -> Unit,
) {
    val scrollState = rememberLazyListState()
    Box {
        LazyColumn(
            modifier = modifier,
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = rememberDrawablePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(top = 4.dp),
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
                thickness = 16.dp
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAppListScreen() {
    val context = LocalContext.current
    repo = TempRepository(context)

    AppListScreen(
        viewModel = AppListActivityViewModel(),
        onBackPress = {},
    )
}
