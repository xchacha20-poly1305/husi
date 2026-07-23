package fr.husi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import fr.husi.compose.SwipeableSnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.BuildConfig
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.platformCombinedClickable
import fr.husi.compose.SagerFab
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.StatsBar
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.setPlainText
import fr.husi.compose.theme.AppTheme
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.libcore.Libcore
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.android
import fr.husi.resources.app_name
import fr.husi.resources.battery_charging_full
import fr.husi.resources.build
import fr.husi.resources.build_environment
import fr.husi.resources.code
import fr.husi.resources.copy_success
import fr.husi.resources.g_translate
import fr.husi.resources.gavel
import fr.husi.resources.github
import fr.husi.resources.ignore_battery_optimizations
import fr.husi.resources.ignore_battery_optimizations_sum
import fr.husi.resources.library_music
import fr.husi.resources.menu
import fr.husi.resources.menu_about
import fr.husi.resources.ok
import fr.husi.resources.oss_licenses
import fr.husi.resources.project
import fr.husi.resources.shuowenxiaozhuan_husi
import fr.husi.resources.translate_platform
import fr.husi.resources.version_x
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onDrawerClick: () -> Unit,
    onNavigateToLibraries: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)
    var showAlertDialog by remember { mutableStateOf<MainViewModelUiEvent.AlertDialog?>(null) }

    val displayVersion = remember(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE) {
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }
    val releaseLink = remember(BuildConfig.VERSION_NAME) {
        val isPreVersion = Libcore.isPreRelease(BuildConfig.VERSION_NAME)
        if (isPreVersion) {
            "https://github.com/xchacha20-poly1305/husi/releases"
        } else {
            "https://github.com/xchacha20-poly1305/husi/releases/latest"
        }
    }
    val boxVersion = remember { Libcore.versionBox() }
    val buildEnvironment = remember { Libcore.buildEnvironment() }

    val shouldRequestBattery = rememberShouldRequestBatteryOptimizations()
    val requestIgnoreBatteryOptimizations = rememberRequestIgnoreBatteryOptimizations()

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    fun putToClipboard(text: String) {
        scope.launch {
            clipboard.setPlainText(text)
            val repo = resolveRepository()
            snackbarState.showSnackbar(
                message = repo.getString(Res.string.copy_success),
                actionLabel = repo.getString(Res.string.ok),
                duration = SnackbarDuration.Short,
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.menu_about)) },
                navigationIcon = PlatformMenuIcon(
                    imageVector = vectorResource(Res.drawable.menu),
                    contentDescription = stringResource(Res.string.menu),
                    onClick = onDrawerClick,
                ),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarState) },
        floatingActionButton = {
            SagerFab(
                visible = scrollHideVisible,
                state = serviceStatus.state,
                showSnackbar = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getStringOrRes(message),
                            actionLabel = resolveRepository().getString(Res.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (serviceStatus.state == ServiceState.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = scrollHideVisible,
                    mainViewModel = mainViewModel,
                )
            }
        },
    ) { innerPadding ->
        val uriHandler = LocalUriHandler.current
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
                item("versions_card") {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val isChinese = Locale.current.language == "zh"
                            CardItem(
                                icon = { Icon(vectorResource(Res.drawable.android), null) },
                                title = stringResource(Res.string.app_name),
                                titleTextStyle = if (isChinese) {
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily(Font(Res.font.shuowenxiaozhuan_husi)),
                                    )
                                } else {
                                    null
                                },
                                description = displayVersion,
                                onClick = { putToClipboard(displayVersion) },
                                onLongClick = { uriHandler.openUri(releaseLink) },
                            )
                            CardItem(
                                icon = {
                                    Icon(
                                        vectorResource(Res.drawable.library_music),
                                        null,
                                    )
                                },
                                title = stringResource(Res.string.version_x, "sing-box"),
                                description = boxVersion,
                                onClick = { putToClipboard(boxVersion) },
                                onLongClick = {
                                    uriHandler.openUri("https://github.com/SagerNet/sing-box")
                                },
                            )
                            CardItem(
                                icon = {
                                    Icon(
                                        vectorResource(Res.drawable.build),
                                        null,
                                    )
                                },
                                title = stringResource(Res.string.build_environment),
                                description = buildEnvironment,
                                onClick = { putToClipboard(buildEnvironment) },
                                onLongClick = {
                                    val isExpert = !DataStore.isExpert
                                    DataStore.isExpert = isExpert
                                    scope.launch {
                                        snackbarState.showSnackbar(
                                            message = "isExpert: $isExpert",
                                            actionLabel = resolveRepository().getString(Res.string.ok),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                            )

                            if (shouldRequestBattery) {
                                CardItem(
                                    icon = {
                                        Icon(
                                            vectorResource(Res.drawable.battery_charging_full),
                                            null,
                                        )
                                    },
                                    title = stringResource(Res.string.ignore_battery_optimizations),
                                    description = stringResource(Res.string.ignore_battery_optimizations_sum),
                                    onClick = { requestIgnoreBatteryOptimizations() },
                                )
                            }
                        }
                    }
                }

                item("project_card") {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.project),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )

                            CardItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                icon = { Icon(vectorResource(Res.drawable.code), null) },
                                title = stringResource(Res.string.github),
                                onClick = { uriHandler.openUri("https://github.com/xchacha20-poly1305/husi") },
                            )
                            CardItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                icon = {
                                    Icon(
                                        vectorResource(Res.drawable.g_translate),
                                        null,
                                    )
                                },
                                title = stringResource(Res.string.translate_platform),
                                onClick = { uriHandler.openUri("https://hosted.weblate.org/projects/husi/husi/") },
                            )
                            CardItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                icon = {
                                    Icon(
                                        vectorResource(Res.drawable.gavel),
                                        null,
                                    )
                                },
                                title = stringResource(Res.string.oss_licenses),
                                onClick = onNavigateToLibraries,
                            )
                        }
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

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = resolveRepository().getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                is MainViewModelUiEvent.AlertDialog -> showAlertDialog = event
            }
        }
    }

    showAlertDialog?.let { dialog ->
        MainViewModelAlertDialog(dialog) {
            showAlertDialog = null
        }
    }
}


@Composable
private fun CardItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {
        Spacer(
            Modifier.size(24.dp),
        )
    },
    title: String,
    titleTextStyle: TextStyle? = null,
    description: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .platformCombinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.size(16.dp))
        Column(
            modifier = modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = titleTextStyle ?: MaterialTheme.typography.titleMedium,
            )
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAboutScreen() {
    ensurePreviewRepository()
    val mainViewModel = koinViewModel<MainViewModel>()

    AppTheme {
        AboutScreen(
            mainViewModel = mainViewModel,
            onDrawerClick = {},
            onNavigateToLibraries = {},
        )
    }
}
