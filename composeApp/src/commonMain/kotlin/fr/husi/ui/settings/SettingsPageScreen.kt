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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import fr.husi.bg.Executable
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.restartApplication
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.apply
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.cag_dns
import fr.husi.resources.cag_misc
import fr.husi.resources.general_settings
import fr.husi.resources.inbound_settings
import fr.husi.resources.need_reload
import fr.husi.resources.need_restart
import fr.husi.resources.ntp_category
import fr.husi.resources.protocol_settings
import fr.husi.resources.route_options
import fr.husi.resources.system_daemon
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.NavRoutes
import fr.husi.ui.StringOrRes
import fr.husi.ui.PlatformDaemonSettingsGroup
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.delay
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SettingsPageScreen(
    kind: NavRoutes.SettingsPage.Kind,
    onBackPress: () -> Unit,
    openAppManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val snackbar = LocalSnackbarEmitter.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onIoDispatcher {
            DataStore.initGlobal()
        }
    }

    fun needReload() {
        if (!DataStore.serviceState.started) return
        snackbar.show(
            StringOrRes.Res(Res.string.need_reload),
            StringOrRes.Res(Res.string.apply),
        ) { result ->
            if (result == SnackbarResult.Dismissed) return@show
            resolveRepository().reloadService()
        }
    }

    fun needRestart() {
        snackbar.show(
            StringOrRes.Res(Res.string.need_restart),
            StringOrRes.Res(Res.string.apply),
        ) { result ->
            if (result == SnackbarResult.Dismissed) return@show
            resolveRepository().stopService()
            runOnDefaultDispatcher {
                delay(500.milliseconds)
                SagerDatabase.instance.close()
                Executable.killAll(true)
                restartApplication()
            }
        }
    }

    val title = when (kind) {
        NavRoutes.SettingsPage.Kind.General -> Res.string.general_settings
        NavRoutes.SettingsPage.Kind.Daemon -> Res.string.system_daemon
        NavRoutes.SettingsPage.Kind.Route -> Res.string.route_options
        NavRoutes.SettingsPage.Kind.Protocol -> Res.string.protocol_settings
        NavRoutes.SettingsPage.Kind.Dns -> Res.string.cag_dns
        NavRoutes.SettingsPage.Kind.Inbound -> Res.string.inbound_settings
        NavRoutes.SettingsPage.Kind.Misc -> Res.string.cag_misc
        NavRoutes.SettingsPage.Kind.Ntp -> Res.string.ntp_category
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(title)) },
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
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    preferenceGroup {
                        when (kind) {
                            NavRoutes.SettingsPage.Kind.General -> GeneralSettingsGroup(
                                needReload = { needReload() },
                                needRestart = { needRestart() },
                            )
                            NavRoutes.SettingsPage.Kind.Daemon -> PlatformDaemonSettingsGroup(
                                showMessage = { message ->
                                    snackbar.show(StringOrRes.Direct(message))
                                },
                            )
                            NavRoutes.SettingsPage.Kind.Route -> RouteSettingsGroup(
                                needReload = { needReload() },
                                openAppManager = openAppManager,
                            )
                            NavRoutes.SettingsPage.Kind.Protocol -> ProtocolSettingsGroup(
                                needReload = { needReload() },
                            )
                            NavRoutes.SettingsPage.Kind.Dns -> DnsSettingsGroup(
                                needReload = { needReload() },
                            )
                            NavRoutes.SettingsPage.Kind.Inbound -> InboundSettingsGroup(
                                needReload = { needReload() },
                            )
                            NavRoutes.SettingsPage.Kind.Misc -> MiscSettingsGroup(
                                needReload = { needReload() },
                                needRestart = { needRestart() },
                            )
                            NavRoutes.SettingsPage.Kind.Ntp -> NtpSettingsGroup(
                                needReload = { needReload() },
                            )
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
    }
}
