package fr.husi.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.SagerFabClearance
import fr.husi.compose.plus
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.backup
import fr.husi.resources.bug_report
import fr.husi.resources.cag_dns
import fr.husi.resources.cag_misc
import fr.husi.resources.cast_connected
import fr.husi.resources.developer_mode
import fr.husi.resources.dns
import fr.husi.resources.file_export
import fr.husi.resources.flight_takeoff
import fr.husi.resources.general_settings
import fr.husi.resources.inbound_settings
import fr.husi.resources.info
import fr.husi.resources.menu_about
import fr.husi.resources.more
import fr.husi.resources.nat
import fr.husi.resources.nfc
import fr.husi.resources.ntp_category
import fr.husi.resources.plugin
import fr.husi.resources.protocol_settings
import fr.husi.resources.route_options
import fr.husi.resources.router
import fr.husi.resources.settings
import fr.husi.resources.system_daemon
import fr.husi.resources.timelapse
import fr.husi.resources.tools_network
import fr.husi.resources.wifi
import fr.husi.ui.NavRoutes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    openSettingsPage: (NavRoutes.SettingsPage.Kind) -> Unit,
    openTool: (NavRoutes.ToolsPage) -> Unit,
    openPlugin: () -> Unit,
    openAbout: () -> Unit,
) {
    val listState = rememberLazyListState()
    val isExpert by DataStore.configurationStore
        .booleanFlow(Key.APP_EXPERT, false)
        .collectAsStateWithLifecycle(false)

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation() +
                PaddingValues(bottom = SagerFabClearance)
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    preferenceGroup {
                        Preference(
                            title = { Text(stringResource(Res.string.general_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.settings,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.General) },
                        )
                        if (!PlatformInfo.isAndroid) {
                            PreferenceDivider()
                            Preference(
                                title = { Text(stringResource(Res.string.system_daemon)) },
                                icon = {
                                    MaskedIcon(
                                        Res.drawable.developer_mode,
                                        color = IconMaskColors.IconLavender,
                                    )
                                },
                                onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Daemon) },
                            )
                        }
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.route_options)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.router,
                                    color = IconMaskColors.IconLightGreen,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Route) },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.protocol_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.flight_takeoff,
                                    color = IconMaskColors.IconLightYellow,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Protocol) },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.cag_dns)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.dns,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Dns) },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.inbound_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.nat,
                                    color = IconMaskColors.IconCoral,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Inbound) },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.cag_misc)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.cast_connected,
                                    color = IconMaskColors.IconWarmGray,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Misc) },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.ntp_category)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.timelapse,
                                    color = IconMaskColors.IconLightPink,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Ntp) },
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.more)) }) }
                    preferenceGroup {
                        Preference(
                            title = { Text(stringResource(Res.string.tools_network)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.wifi,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            onClick = { openTool(NavRoutes.ToolsPage.Network) },
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.backup)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.file_export,
                                    color = IconMaskColors.IconLightYellow,
                                )
                            },
                            onClick = { openTool(NavRoutes.ToolsPage.Backup) },
                        )
                        if (isExpert) {
                            PreferenceDivider()
                            Preference(
                                title = { Text("DEBUG") },
                                icon = {
                                    MaskedIcon(
                                        Res.drawable.bug_report,
                                        color = IconMaskColors.IconCoral,
                                    )
                                },
                                onClick = { openTool(NavRoutes.ToolsPage.Debug) },
                            )
                        }
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.plugin)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.nfc,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            onClick = openPlugin,
                        )
                        PreferenceDivider()
                        Preference(
                            title = { Text(stringResource(Res.string.menu_about)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.info,
                                    color = IconMaskColors.IconLavender,
                                )
                            },
                            onClick = openAbout,
                        )
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
    }
}
