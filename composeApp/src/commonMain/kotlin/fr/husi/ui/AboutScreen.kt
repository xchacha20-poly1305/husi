package fr.husi.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.BuildConfig
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.SagerFab
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.StatsBar
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.theme.AppTheme
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.libcore.Libcore
import fr.husi.repository.FakeRepository
import fr.husi.repository.repo
import fr.husi.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onDrawerClick: () -> Unit,
    onNavigateToLibraries: () -> Unit,
) {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)

    val displayVersion = remember {
        var displayVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        if (BuildConfig.DEBUG) {
            displayVersion += " DEBUG"
        }
        displayVersion
    }
    val releaseLink = remember {
        val isPreVersion = Libcore.isPreRelease(BuildConfig.VERSION_NAME)
        if (isPreVersion) {
            "https://github.com/xchacha20-poly1305/husi/releases"
        } else {
            "https://github.com/xchacha20-poly1305/husi/releases/latest"
        }
    }
    val coreVersion = remember { Libcore.version() }

    val shouldRequestBattery = rememberShouldRequestBatteryOptimizations()

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.menu_about)) },
                navigationIcon = {
                    PlatformMenuIcon(
                        imageVector = vectorResource(Res.drawable.menu),
                        contentDescription = stringResource(Res.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            SagerFab(
                visible = scrollHideVisible,
                state = serviceStatus.state,
                showSnackbar = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getStringOrRes(message),
                            actionLabel = repo.getString(Res.string.ok),
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding.withNavigation(),
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
                            onCLick = {
                                uriHandler.openUri(releaseLink)
                            },
                        )
                        CardItem(
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.library_music),
                                    null,
                                )
                            },
                            title = stringResource(Res.string.version_x, "sing-box"),
                            description = coreVersion,
                            onCLick = {
                                uriHandler.openUri("https://github.com/SagerNet/sing-box")
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
                                onCLick = { requestIgnoreBatteryOptimizations() },
                            )
                        }
                        CardItem(
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.public_icon),
                                    null,
                                )
                            },
                            title = stringResource(Res.string.sekai),
                            onCLick = {
                                uriHandler.openUri("https://sekai.icu/sponsor")
                            },
                            onLongClick = {
                                val isExpert = !DataStore.isExpert
                                DataStore.isExpert = isExpert
                                scope.launch {
                                    snackbarState.showSnackbar(
                                        message = "isExpert: $isExpert",
                                        actionLabel = repo.getString(Res.string.ok),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                        )
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
                            onCLick = { uriHandler.openUri("https://github.com/xchacha20-poly1305/husi") },
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
                            onCLick = { uriHandler.openUri("https://hosted.weblate.org/projects/husi/husi/") },
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
                            onCLick = onNavigateToLibraries,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = repo.getString(Res.string.ok),
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

                else -> {}
            }
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
    onCLick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .combinedClickable(
                onClick = onCLick,
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
    repo = FakeRepository()
    val mainViewModel = viewModel<MainViewModel>()

    AppTheme {
        AboutScreen(
            mainViewModel = mainViewModel,
            onDrawerClick = {},
            onNavigateToLibraries = {},
        )
    }
}
