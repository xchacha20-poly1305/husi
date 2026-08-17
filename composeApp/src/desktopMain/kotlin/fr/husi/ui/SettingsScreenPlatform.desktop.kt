package fr.husi.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.DesktopAutoStart
import fr.husi.Key
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.TextButton
import fr.husi.compose.ValidatedTextField
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.validateTunInterfaceName
import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.platform.PlatformInfo
import fr.husi.repository.DaemonInstallResult
import fr.husi.repository.DaemonOwner
import fr.husi.repository.installDaemon
import fr.husi.repository.resolveDesktopRepository
import fr.husi.resources.Res
import fr.husi.resources.arrow_and_edge
import fr.husi.resources.auto_connect_desktop
import fr.husi.resources.auto_connect_summary_desktop
import fr.husi.resources.daemon_in_use
import fr.husi.resources.daemon_in_use_summary
import fr.husi.resources.daemon_install_cancelled
import fr.husi.resources.daemon_install_failed
import fr.husi.resources.daemon_install_no_elevation
import fr.husi.resources.daemon_install_success
import fr.husi.resources.daemon_installed_summary
import fr.husi.resources.daemon_stay_read_only
import fr.husi.resources.daemon_takeover
import fr.husi.resources.daemon_takeover_message
import fr.husi.resources.daemon_takeover_title
import fr.husi.resources.flight_takeoff
import fr.husi.resources.install_daemon
import fr.husi.resources.install_daemon_summary
import fr.husi.resources.phonelink_ring
import fr.husi.resources.security
import fr.husi.resources.start_at_boot_daemon
import fr.husi.resources.start_at_boot_daemon_summary
import fr.husi.resources.tun_auto_redirect
import fr.husi.resources.tun_interface_name
import fr.husi.resources.tun_interface_name_summary
import fr.husi.resources.tun_strict_route
import fr.husi.resources.update
import fr.husi.resources.update_daemon
import fr.husi.resources.update_daemon_summary
import fr.husi.resources.warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

@Composable
internal actual fun AutoConnectPreference() {
    val value by DataStore.configurationStore
        .booleanFlow(Key.PERSIST_ACROSS_REBOOT, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = value,
        onValueChange = {
            if (DesktopAutoStart.setEnabled(it)) {
                DataStore.persistAcrossReboot = it
            }
        },
        title = { Text(stringResource(Res.string.auto_connect_desktop)) },
        icon = {
            MaskedIcon(
                Res.drawable.phonelink_ring,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.auto_connect_summary_desktop)) },
    )
}

@Composable
internal actual fun PlatformDaemonSettingsGroup(showMessage: (String) -> Unit) {
    DaemonOptionsGroup(showMessage = showMessage)
}

@Composable
private fun DaemonOptionsGroup(showMessage: (String) -> Unit) {
    val repository = resolveDesktopRepository()
    val hostState by repository.coreHostState.collectAsStateWithLifecycle()
    val coreClient = koinInject<CoreClient>()
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var takingOver by remember { mutableStateOf(false) }
    var showTakeOverDialog by remember { mutableStateOf(false) }
    var startAtBoot by remember { mutableStateOf(false) }
    var startAtBootLoaded by remember { mutableStateOf(false) }
    val foreignOwner = hostState.foreignOwner

    val showStartAtBoot = hostState.isDaemon

    LaunchedEffect(hostState.isDaemon, hostState.apiVersionMismatch) {
        if (!showStartAtBoot) {
            startAtBootLoaded = false
            return@LaunchedEffect
        }
        startAtBootLoaded = false
        runCatching {
            coreClient.getDaemonInfo().startAtBoot
        }.onSuccess { enabled ->
            startAtBoot = enabled
            startAtBootLoaded = true
        }.onFailure {
            Logs.w("getDaemonInfo for start-at-boot failed: ${it.message}")
            startAtBootLoaded = false
        }
    }

    val needsUpdate = hostState.apiVersionMismatch
    Preference(
        title = {
            Text(
                stringResource(
                    if (needsUpdate) {
                        Res.string.update_daemon
                    } else {
                        Res.string.install_daemon
                    },
                ),
            )
        },
        icon = {
            MaskedIcon(
                if (needsUpdate) {
                    Res.drawable.update
                } else {
                    Res.drawable.security
                },
                color = if (needsUpdate) {
                    IconMaskColors.IconCoral
                } else {
                    IconMaskColors.IconCyan
                },
            )
        },
        summary = {
            Text(
                stringResource(
                    when {
                        needsUpdate -> Res.string.update_daemon_summary
                        hostState.isDaemon -> Res.string.daemon_installed_summary
                        else -> Res.string.install_daemon_summary
                    },
                ),
            )
        },
        enabled = !installing && !takingOver,
        onClick = {
            if (installing) return@Preference
            installing = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { installDaemon() }
                    when (result) {
                        DaemonInstallResult.Success -> {
                            runCatching {
                                repository.reattachDaemon()
                            }.onFailure {
                                Logs.w("reattach after daemon install failed", it)
                                showMessage(it.readableMessage)
                                return@launch
                            }
                            showMessage(getString(Res.string.daemon_install_success))
                        }

                        DaemonInstallResult.Cancelled -> {
                            showMessage(getString(Res.string.daemon_install_cancelled))
                        }

                        is DaemonInstallResult.ElevationUnavailable -> {
                            showMessage(
                                getString(
                                    Res.string.daemon_install_no_elevation,
                                    result.program,
                                ),
                            )
                        }

                        is DaemonInstallResult.Failed -> {
                            showMessage(
                                getString(Res.string.daemon_install_failed, result.message),
                            )
                        }
                    }
                } finally {
                    installing = false
                }
            }
        },
    )

    if (foreignOwner != null) {
        PreferenceDivider()
        Preference(
            title = {
                Text(
                    stringResource(
                        Res.string.daemon_in_use,
                        foreignOwner.name.ifBlank { foreignOwner.id },
                    ),
                )
            },
            icon = {
                MaskedIcon(
                    Res.drawable.warning,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.risk(),
                )
            },
            summary = { Text(stringResource(Res.string.daemon_in_use_summary)) },
            enabled = !installing && !takingOver,
            onClick = { showTakeOverDialog = true },
        )
    }

    if (showTakeOverDialog) {
        val owner = hostState.foreignOwner
        if (owner != null) {
            DaemonTakeOverDialog(
                owner = owner,
                onTakeOver = {
                    showTakeOverDialog = false
                    takingOver = true
                    scope.launch {
                        try {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    repository.takeOverDaemon()
                                }
                            }.onFailure {
                                Logs.w("takeOverDaemon failed", it)
                                showMessage(it.readableMessage)
                                return@launch
                            }
                            runCatching {
                                repository.reattachDaemon()
                            }.onFailure {
                                Logs.w("reattach after takeover failed", it)
                                showMessage(it.readableMessage)
                            }
                        } finally {
                            takingOver = false
                        }
                    }
                },
                onStayReadOnly = { showTakeOverDialog = false },
            )
        }
    }

    if (showStartAtBoot) {
        PreferenceDivider()
        SwitchPreference(
            value = startAtBoot,
            onValueChange = { enabled ->
                val previous = startAtBoot
                startAtBoot = enabled
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            coreClient.setStartAtBoot(enabled)
                        }
                    }.onFailure {
                        startAtBoot = previous
                        Logs.w("setStartAtBoot failed", it)
                        showMessage(it.readableMessage)
                    }
                }
            },
            title = { Text(stringResource(Res.string.start_at_boot_daemon)) },
            icon = {
                MaskedIcon(
                    Res.drawable.flight_takeoff,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(stringResource(Res.string.start_at_boot_daemon_summary)) },
            enabled = startAtBootLoaded && !installing && !takingOver,
        )
    }
}

@Composable
private fun DaemonTakeOverDialog(
    owner: DaemonOwner,
    onTakeOver: () -> Unit,
    onStayReadOnly: () -> Unit,
) {
    val ownerLabel = owner.name.ifBlank { owner.id }
    AlertDialog(
        onDismissRequest = onStayReadOnly,
        confirmButton = {
            TextButton(stringResource(Res.string.daemon_takeover), onClick = onTakeOver)
        },
        dismissButton = {
            TextButton(stringResource(Res.string.daemon_stay_read_only), onClick = onStayReadOnly)
        },
        icon = {
            Icon(vectorResource(Res.drawable.warning), null)
        },
        title = { Text(stringResource(Res.string.daemon_takeover_title)) },
        text = { Text(stringResource(Res.string.daemon_takeover_message, ownerLabel)) },
    )
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit = {}

@Composable
internal actual fun PlatformGeneralOptions(needReload: () -> Unit) {
}

@Composable
internal actual fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    val tunInterfaceNameValue by DataStore.configurationStore
        .stringFlow(Key.TUN_INTERFACE_NAME, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = tunInterfaceNameValue,
        onValueChange = {
            DataStore.tunInterfaceName = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tun_interface_name)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.arrow_and_edge,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = {
            val text = tunInterfaceNameValue.ifBlank {
                stringResource(Res.string.tun_interface_name_summary)
            }
            Text(text)
        },
        valueToText = { it },
        enabled = isVpnMode,
    ) { value, onValueChange, onOk ->
        ValidatedTextField(
            value = value,
            onValueChange = onValueChange,
            onOk = onOk,
            validator = ::validateTunInterfaceName,
        )
    }

    val strictRouteValue by DataStore.configurationStore
        .booleanFlow(Key.TUN_STRICT_ROUTE, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = strictRouteValue,
        onValueChange = {
            DataStore.tunStrictRoute = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tun_strict_route)) },
        icon = {
            MaskedIcon(
                Res.drawable.arrow_and_edge,
                color = IconMaskColors.IconCyan,
            )
        },
        enabled = isVpnMode,
    )
    if (PlatformInfo.isLinux) {
        val autoRedirectValue by DataStore.configurationStore
            .booleanFlow(Key.TUN_AUTO_REDIRECT, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = autoRedirectValue,
            onValueChange = {
                DataStore.tunAutoRedirect = it
                needReload()
            },
            title = { Text(stringResource(Res.string.tun_auto_redirect)) },
            icon = {
                MaskedIcon(
                    Res.drawable.arrow_and_edge,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            enabled = isVpnMode,
        )
    }
}

@Composable
internal actual fun ProxyAppsPreferences(openAppManager: () -> Unit) {
}

@Composable
internal actual fun PlatformSecurityOptions() {
}

@Composable
internal actual fun MeteredNetworkPreference(needReload: () -> Unit) {
}

@Composable
internal actual fun HttpProxyBypassPreference(enabled: Boolean, needReload: () -> Unit) {
}

@Composable
internal actual fun PlatformMiscOptions(needReload: () -> Unit) {
}

@Composable
internal actual fun rememberThemeExtraColors(): List<Color> = emptyList()

@Composable
internal actual fun rememberAppLanguageController(defaultTag: String): AppLanguageController {
    val flow = DataStore.configurationStore
        .stringFlow(Key.APP_LANGUAGE, defaultTag)
    val state by flow.collectAsStateWithLifecycle(defaultTag)
    return object : AppLanguageController {
        override var value: String
            get() = state.ifBlank { defaultTag }
            set(value) {
                DataStore.appLanguage = value
            }
        override val flow = flow
    }
}

@Composable
internal actual fun DisableProcessTextPreference() {
}

@Composable
internal actual fun HideLauncherIconPreference() {
}
