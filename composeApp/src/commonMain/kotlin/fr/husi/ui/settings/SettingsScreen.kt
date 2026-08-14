package fr.husi.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.bg.BackendState
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.SagerFab
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.SwipeableSnackbarHost
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.withNavigation
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.cag_dns
import fr.husi.resources.cag_misc
import fr.husi.resources.cast_connected
import fr.husi.resources.construction
import fr.husi.resources.developer_mode
import fr.husi.resources.dns
import fr.husi.resources.flight_takeoff
import fr.husi.resources.general_settings
import fr.husi.resources.inbound_settings
import fr.husi.resources.info
import fr.husi.resources.menu_about
import fr.husi.resources.menu_tools
import fr.husi.resources.more
import fr.husi.resources.nat
import fr.husi.resources.nfc
import fr.husi.resources.ntp_category
import fr.husi.resources.ok
import fr.husi.resources.plugin
import fr.husi.resources.protocol_settings
import fr.husi.resources.route_options
import fr.husi.resources.router
import fr.husi.resources.settings
import fr.husi.resources.system_daemon
import fr.husi.resources.timelapse
import fr.husi.ui.MainViewModel
import fr.husi.ui.MainViewModelAlertDialog
import fr.husi.ui.MainViewModelUiEvent
import fr.husi.ui.NavRoutes
import fr.husi.ui.getStringOrRes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    openSettingsPage: (NavRoutes.SettingsPage.Kind) -> Unit,
    openTools: () -> Unit,
    openPlugin: () -> Unit,
    openAbout: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)
    var showAlertDialog by remember { mutableStateOf<MainViewModelUiEvent.AlertDialog?>(null) }

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = null,
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
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
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
                            title = { Text(stringResource(Res.string.menu_tools)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.construction,
                                    color = IconMaskColors.IconLightOrange,
                                )
                            },
                            onClick = openTools,
                        )
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
