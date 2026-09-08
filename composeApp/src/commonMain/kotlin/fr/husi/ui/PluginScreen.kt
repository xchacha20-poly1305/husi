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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.platformCombinedClickable
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.material3.Text
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.restartApplication
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.download
import fr.husi.resources.need_restart
import fr.husi.resources.nfc
import fr.husi.resources.no_plugin_found
import fr.husi.resources.ok
import fr.husi.resources.plugin
import fr.husi.resources.plugin_not_installed
import fr.husi.resources.plugin_not_installed_summary
import fr.husi.resources.version_x
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.enums.enumEntries

@Composable
fun PluginScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
) {
    val plugins by platformPluginsFlow().collectAsStateWithLifecycle(emptyList())

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbar = LocalSnackbarEmitter.current
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val openPluginCard = rememberOpenPluginCard()

    val isExpert by DataStore.isExpert.collectAsStateWithLifecycle()

    val notInstalledPlugins = remember(plugins) {
        val installedIDs = plugins.mapTo(mutableSetOf()) { it.id }
        enumEntries<PluginEntry>().filter { it.pluginId !in installedIDs }
    }

    fun needRestart() {
        snackbar.show(
            StringOrRes.Res(Res.string.need_restart),
            StringOrRes.Res(Res.string.ok),
        ) { result ->
            if (result == SnackbarResult.ActionPerformed) {
                restartApplication()
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.plugin)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = contentPadding,
                ) {
                    installedPlugins(plugins, openPluginCard, uriHandler::openUri)
                    notInstalledPlugins(notInstalledPlugins) {
                        uriHandler.openUri(it.platformDownloadUri)
                    }
                    platformPluginPreferences(isExpert, ::needRestart)
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
    }

}

private fun LazyListScope.installedPlugins(
    plugins: List<PluginDisplay>,
    openPluginCard: (PluginDisplay) -> Unit,
    openUri: (String) -> Unit,
) {
    item("plugins_card") {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            if (plugins.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (plugin in plugins) {
                        PluginCardItem(
                            icon = {
                                MaskedIcon(
                                    Res.drawable.nfc,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            title = stringResource(
                                Res.string.version_x,
                                plugin.id,
                            ) + " (${plugin.provider})",
                            description = "v${plugin.version}",
                            onClick = { openPluginCard(plugin) },
                            onLongClick = {
                                plugin.entry?.let { openUri(it.platformDownloadUri) }
                            },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.no_plugin_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private val PluginEntry.platformDownloadUri: String
    get() = if (PlatformInfo.isAndroid) downloadSource.apk else downloadSource.binary

private fun LazyListScope.notInstalledPlugins(
    plugins: List<PluginEntry>,
    onDownload: (PluginEntry) -> Unit,
) {
    if (plugins.isEmpty()) return
    item("not_installed_plugins_card") {
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
                Text(
                    text = stringResource(Res.string.plugin_not_installed),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
                for (plugin in plugins) {
                    PluginCardItem(
                        icon = {
                            MaskedIcon(
                                Res.drawable.download,
                                color = IconMaskColors.IconLightGreen,
                            )
                        },
                        title = stringResource(plugin.displayName),
                        description = stringResource(Res.string.plugin_not_installed_summary),
                        onClick = { onDownload(plugin) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginCardItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { Spacer(Modifier.size(24.dp)) },
    title: String,
    description: String? = null,
    titleTextStyle: TextStyle? = null,
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
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.size(16.dp))
        Column(
            modifier = Modifier.weight(1f),
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
