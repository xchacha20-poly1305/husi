package fr.husi.ui.settings

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import fr.husi.bg.Executable
import fr.husi.compose.preferenceGroup
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.restartApplication
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.app_update_settings
import fr.husi.resources.apply
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
import fr.husi.ui.PlatformDaemonSettingsGroup
import fr.husi.ui.StringOrRes
import fr.husi.ui.platformAppUpdateSettings
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SettingsPageScreen(
    kind: NavRoutes.SettingsPage.Kind,
    onBackPress: () -> Unit,
    openAppManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = LocalSnackbarEmitter.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
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
        NavRoutes.SettingsPage.Kind.AppUpdate -> Res.string.app_update_settings
    }

    SettingsPage(title = title, modifier = modifier, onBackPress = onBackPress) {
        when (kind) {
            NavRoutes.SettingsPage.Kind.General -> preferenceGroup {
                GeneralSettingsGroup(
                    needReload = { needReload() },
                    needRestart = { needRestart() },
                    showMessage = { message ->
                        snackbar.show(StringOrRes.Direct(message))
                    },
                )
            }
            NavRoutes.SettingsPage.Kind.Daemon -> preferenceGroup {
                PlatformDaemonSettingsGroup(
                    showMessage = { message ->
                        snackbar.show(StringOrRes.Direct(message))
                    },
                )
            }
            NavRoutes.SettingsPage.Kind.Route -> preferenceGroup {
                RouteSettingsGroup(
                    needReload = { needReload() },
                    openAppManager = openAppManager,
                )
            }
            NavRoutes.SettingsPage.Kind.Protocol -> preferenceGroup {
                ProtocolSettingsGroup(
                    needReload = { needReload() },
                )
            }
            NavRoutes.SettingsPage.Kind.Dns -> preferenceGroup {
                DnsSettingsGroup(
                    needReload = { needReload() },
                )
            }
            NavRoutes.SettingsPage.Kind.Inbound -> preferenceGroup {
                InboundSettingsGroup(
                    needReload = { needReload() },
                )
            }
            NavRoutes.SettingsPage.Kind.Misc -> preferenceGroup {
                MiscSettingsGroup(
                    needReload = { needReload() },
                    needRestart = { needRestart() },
                )
            }
            NavRoutes.SettingsPage.Kind.Ntp -> preferenceGroup {
                NtpSettingsGroup(
                    needReload = { needReload() },
                )
            }
            NavRoutes.SettingsPage.Kind.AppUpdate -> platformAppUpdateSettings()
        }
    }
}
