package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.SagerFab
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.StatsBar
import io.nekohasekai.sagernet.compose.paddingWithNavigation
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.repository.TempRepository
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.launch
import libcore.Libcore

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    viewModel: AboutScreenViewModel = viewModel(),
    onDrawerClick: () -> Unit,
    connection: SagerConnection,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
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

    val shouldRequestBatteryOptimizations = remember(context) {
        !(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    val serviceStatus by connection.status.collectAsStateWithLifecycle()
    val service by connection.service.collectAsStateWithLifecycle()

    val annotatedLicense = buildAnnotatedString {
        val links = listOf(
            "HystericalDragons@proton.me" to "mailto:HystericalDragons@proton.me",
            "HystericalDragon@protomail.com" to "mailto:HystericalDragon@protomail.com",
            "contact-sagernet@sekai.icu" to "mailto:contact-sagernet@sekai.icu",
            "http://www.gnu.org/licenses/" to "http://www.gnu.org/licenses/",
        )
        val sortedLinks = links.map { (text, url) ->
            val index = LICENSE.indexOf(text)
            Triple(index, text, url)
        }.sortedBy { it.first }

        var lastIndex = 0
        sortedLinks.forEach { (index, text, url) ->
            append(LICENSE.substring(lastIndex, index))
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(text)
            }
            lastIndex = index + text.length
        }
        append(LICENSE.substring(lastIndex))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SimpleTopAppBar(
                title = {Text(stringResource(R.string.menu_about))},
                navigationIcon = ImageVector.vectorResource(R.drawable.menu),
                navigationDescription = stringResource(R.string.menu),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
                onNavigationClick = onDrawerClick,
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
                            message = context.getStringOrRes(message),
                            actionLabel = context.getString(android.R.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (serviceStatus.state == BaseService.State.Connected) {
                StatsBar(
                    visible = scrollHideVisible,
                    mainViewModel = mainViewModel,
                    service = service,
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
            contentPadding = innerPadding.paddingWithNavigation(),
        ) {
            item("info_card") {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {}, // Make ripple
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = stringResource(R.string.app_name_long),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.app_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

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
                        CardItem(
                            icon = { Icon(ImageVector.vectorResource(R.drawable.android), null) },
                            title = stringResource(R.string.app_name),
                            description = displayVersion,
                            onCLick = {
                                uriHandler.openUri(releaseLink)
                            },
                        )
                        CardItem(
                            icon = {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.library_music),
                                    null,
                                )
                            },
                            title = stringResource(R.string.version_x, "sing-box"),
                            description = coreVersion,
                            onCLick = {
                                uriHandler.openUri("https://github.com/SagerNet/sing-box")
                            },
                        )

                        for (plugin in uiState.plugins) {
                            CardItem(
                                icon = { Icon(ImageVector.vectorResource(R.drawable.nfc), null) },
                                title = stringResource(R.string.version_x, plugin.id)
                                        + " (${plugin.provider})",
                                description = "v${plugin.version}",
                                onCLick = {
                                    context.startActivity(
                                        Intent()
                                            .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(
                                                Uri.fromParts(
                                                    "package",
                                                    plugin.packageName,
                                                    null,
                                                ),
                                            ),
                                    )
                                },
                                onLongClick = {
                                    plugin.entry?.let {
                                        uriHandler.openUri(it.downloadSource.downloadLink)
                                    }
                                },
                            )
                        }

                        if (shouldRequestBatteryOptimizations) {
                            CardItem(
                                icon = {
                                    Icon(
                                        ImageVector.vectorResource(R.drawable.battery_charging_full),
                                        null,
                                    )
                                },
                                title = stringResource(R.string.ignore_battery_optimizations),
                                description = stringResource(R.string.ignore_battery_optimizations_sum),
                                onCLick = {
                                    @SuppressLint("BatteryLife")
                                    context.startActivity(
                                        Intent()
                                            .setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                            .setData("package:${context.packageName}".toUri()),
                                    )
                                },
                            )
                        }
                        CardItem(
                            icon = {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.public_icon),
                                    null,
                                )
                            },
                            title = stringResource(R.string.sekai),
                            onCLick = {
                                uriHandler.openUri("https://sekai.icu/sponsor")
                            },
                            onLongClick = {
                                val isExpert = !DataStore.isExpert
                                DataStore.isExpert = isExpert
                                scope.launch {
                                    snackbarState.showSnackbar(
                                        message = "isExpert: $isExpert",
                                        actionLabel = context.getString(android.R.string.ok),
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
                            text = stringResource(R.string.project),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )

                        CardItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            icon = { Icon(ImageVector.vectorResource(R.drawable.code), null) },
                            title = stringResource(R.string.github),
                            onCLick = { uriHandler.openUri("https://github.com/xchacha20-poly1305/husi") },
                        )
                        CardItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            icon = {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.g_translate),
                                    null,
                                )
                            },
                            title = stringResource(R.string.translate_platform),
                            onCLick = { uriHandler.openUri("https://hosted.weblate.org/projects/husi/husi/") },
                        )
                    }
                }
            }

            item("license_card") {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.license),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SelectionContainer {
                            Text(
                                text = annotatedLicense,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getStringOrRes(event.actionLabel),
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
    @SuppressLint("ComposableLambdaParameterNaming") icon: @Composable () -> Unit = {
        Spacer(
            Modifier.size(24.dp),
        )
    },
    title: String,
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
                style = MaterialTheme.typography.titleMedium,
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
    val context = LocalContext.current
    repo = TempRepository(context)

    AppTheme {
        AboutScreen(
            mainViewModel = MainViewModel(),
            viewModel = AboutScreenViewModel(),
            onDrawerClick = {},
            connection = SagerConnection(0, false),
        )
    }
}