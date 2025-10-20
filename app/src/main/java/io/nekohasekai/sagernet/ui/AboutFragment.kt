package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.ComposeHolderBinding
import io.nekohasekai.sagernet.repository.TempRepository
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.launch
import libcore.Libcore

@OptIn(ExperimentalMaterial3Api::class)
class AboutFragment : OnKeyDownFragment(R.layout.compose_holder) {

    private val viewModel by viewModels<AboutFragmentViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = ComposeHolderBinding.bind(view)
        binding.root.setContent {
            AppTheme {
                AboutScreen(
                    viewModel = viewModel,
                    openDrawer = {
                        (requireActivity() as MainActivity).binding.drawerLayout
                            .openDrawer(GravityCompat.START)
                    },
                )
            }
        }
    }

}

@Composable
private fun AboutScreen(
    modifier: Modifier = Modifier,
    viewModel: AboutFragmentViewModel,
    openDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
                        )
                    )
                )
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
                title = R.string.menu_about,
                navigationIcon = Icons.Filled.Menu,
                navigationDescription = stringResource(R.string.menu),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
                onNavigationClick = openDrawer,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val uriHandler = LocalUriHandler.current

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                bottom = innerPadding.calculateBottomPadding() +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
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
                            icon = { Icon(Icons.Filled.Android, null) },
                            title = stringResource(R.string.app_name),
                            description = displayVersion,
                            onCLick = {
                                uriHandler.openUri(releaseLink)
                            },
                        )
                        CardItem(
                            icon = { Icon(Icons.Filled.LibraryMusic, null) },
                            title = stringResource(R.string.version_x, "sing-box"),
                            description = coreVersion,
                            onCLick = {
                                uriHandler.openUri("https://github.com/SagerNet/sing-box")
                            },
                        )

                        for (plugin in uiState.plugins) {
                            CardItem(
                                icon = { Icon(Icons.Filled.Nfc, null) },
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
                                                    null
                                                )
                                            ),
                                    )
                                },
                                onLongClick = {
                                    plugin.entry?.let {
                                        uriHandler.openUri(it.downloadSource.downloadLink)
                                    }
                                }
                            )
                        }

                        if (shouldRequestBatteryOptimizations) {
                            CardItem(
                                icon = { Icon(Icons.Filled.BatteryChargingFull, null) },
                                title = stringResource(R.string.ignore_battery_optimizations),
                                description = stringResource(R.string.ignore_battery_optimizations_sum),
                                onCLick = {
                                    @SuppressLint("BatteryLife")
                                    context.startActivity(
                                        Intent()
                                            .setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                            .setData("package:${context.packageName}".toUri())
                                    )
                                },
                            )
                        }
                        CardItem(
                            icon = { Icon(Icons.Filled.Public, null) },
                            title = stringResource(R.string.sekai),
                            onCLick = {
                                uriHandler.openUri("https://sekai.icu/sponsor")
                            },
                            onLongClick = {
                                val isExpert = !DataStore.isExpert
                                DataStore.isExpert = isExpert
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "isExpert: $isExpert",
                                        actionLabel = context.getString(android.R.string.ok),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            }
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
                            icon = { Icon(Icons.Filled.Code, null) },
                            title = stringResource(R.string.github),
                            onCLick = { uriHandler.openUri("https://github.com/xchacha20-poly1305/husi") },
                        )
                        CardItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            icon = { Icon(Icons.Filled.GTranslate, null) },
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
}


@Composable
private fun CardItem(
    modifier: Modifier = Modifier,
    @SuppressLint("ComposableLambdaParameterNaming") icon: @Composable () -> Unit = {
        Spacer(
            Modifier.size(24.dp)
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
        verticalAlignment = Alignment.CenterVertically
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
            viewModel = AboutFragmentViewModel(),
            openDrawer = {},
        )
    }
}