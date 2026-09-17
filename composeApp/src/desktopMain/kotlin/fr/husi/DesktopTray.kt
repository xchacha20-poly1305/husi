package fr.husi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.Painter
import dev.nucleusframework.composenativetray.menu.api.ComposableTrayMenuScope
import dev.nucleusframework.composenativetray.menu.api.KeyShortcut
import dev.nucleusframework.composenativetray.tray.api.Tray
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.compose.setSystemClipboardPlainText
import fr.husi.database.DataStore
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import fr.husi.repository.DesktopRepository
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.resources.close
import fr.husi.resources.content_copy
import fr.husi.resources.copy_terminal_proxy
import fr.husi.resources.exit
import fr.husi.resources.ic_service_active
import fr.husi.resources.service_mode
import fr.husi.resources.service_mode_proxy
import fr.husi.resources.service_mode_vpn
import fr.husi.resources.start
import fr.husi.resources.stop
import fr.husi.resources.system_proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.nucleusframework.composenativetray.menu.api.Key as TrayKey

private const val WINDOWS_MNEMONIC_MARKER = '&'
private const val LINUX_MNEMONIC_MARKER = '_'

private val trayMnemonicMarker: Char? = when (PlatformInfo.platform) {
    Platform.Windows -> WINDOWS_MNEMONIC_MARKER
    Platform.Linux -> LINUX_MNEMONIC_MARKER
    else -> null
}

private fun trayMenuText(label: String, accessKey: TrayKey? = null): String {
    val mnemonicMarker = trayMnemonicMarker ?: return label
    val escapedLabel = label.replace(
        mnemonicMarker.toString(),
        "$mnemonicMarker$mnemonicMarker",
    )
    if (accessKey == null) {
        return escapedLabel
    }
    val accessLetter = accessKey.name.single()
    val letterIndex = escapedLabel.indexOf(accessLetter, ignoreCase = true)
    return if (letterIndex >= 0) {
        StringBuilder(escapedLabel).insert(letterIndex, mnemonicMarker).toString()
    } else {
        "$escapedLabel($mnemonicMarker$accessLetter)"
    }
}

private fun trayMenuShortcut(accessKey: TrayKey?): KeyShortcut? {
    if (trayMnemonicMarker != null) {
        return null
    }
    return accessKey?.let { KeyShortcut(it) }
}

@Composable
private fun ComposableTrayMenuScope.TrayItem(
    label: String,
    accessKey: TrayKey? = null,
    icon: Painter? = null,
    onClick: () -> Unit,
) {
    val text = trayMenuText(label, accessKey)
    val shortcut = trayMenuShortcut(accessKey)
    if (icon == null) {
        Item(label = text, shortcut = shortcut, onClick = onClick)
    } else {
        Item(label = text, icon = icon, shortcut = shortcut, onClick = onClick)
    }
}

@Composable
private fun ComposableTrayMenuScope.TrayCheckableItem(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    CheckableItem(
        label = trayMenuText(label),
        checked = checked,
        onCheckedChange = onCheckedChange,
        isEnabled = enabled,
    )
}

@Composable
private fun ComposableTrayMenuScope.TraySubMenu(
    label: String,
    submenuContent: @Composable ComposableTrayMenuScope.() -> Unit,
) {
    SubMenu(label = trayMenuText(label), submenuContent = submenuContent)
}

/** The tray icon and its menu, the app's only handle once the window is hidden. */
@Composable
internal fun HusiTray(
    repository: DesktopRepository,
    onOpenWindow: () -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val serviceStatus by BackendState.status.collectAsState()
    val serviceMode by DataStore.serviceMode.flow()
        .collectAsState(Key.MODE_VPN)
    val systemProxyEnabled by DataStore.systemProxy.flow()
        .collectAsState(false)
    val hasInboundAuth by DataStore.hasInboundAuthFlow()
        .collectAsState(false)

    fun setServiceMode(mode: String) {
        if (DataStore.serviceMode.getBlocking() == mode) return
        DataStore.serviceMode.setBlocking(mode)
        if (serviceStatus.state.canStop) {
            repository.reloadService()
        }
    }

    Tray(
        icon = painterResource(Res.drawable.ic_service_active),
        tooltip = stringResource(Res.string.app_name),
        primaryAction = onOpenWindow,
        menuContent = {
            TrayItem(
                label = serviceStatus.profileName ?: stringResource(Res.string.app_name),
                accessKey = TrayKey.O,
                onClick = onOpenWindow,
            )
            TrayItem(
                label = stringResource(
                    if (serviceStatus.state == ServiceState.Connected) {
                        Res.string.stop
                    } else {
                        Res.string.start
                    },
                ),
                accessKey = TrayKey.S,
            ) {
                when (serviceStatus.state) {
                    ServiceState.Stopped -> repository.startService()
                    ServiceState.Idle, ServiceState.Connected -> repository.stopService()
                    else -> {}
                }
            }
            TraySubMenu(label = stringResource(Res.string.service_mode)) {
                TrayCheckableItem(
                    label = stringResource(Res.string.service_mode_proxy),
                    checked = serviceMode == Key.MODE_PROXY,
                    onCheckedChange = { isSelected ->
                        if (isSelected) setServiceMode(Key.MODE_PROXY)
                    },
                )
                TrayCheckableItem(
                    label = stringResource(Res.string.service_mode_vpn),
                    checked = serviceMode == Key.MODE_VPN,
                    onCheckedChange = { isSelected ->
                        if (isSelected) setServiceMode(Key.MODE_VPN)
                    },
                )
            }
            TrayCheckableItem(
                label = stringResource(Res.string.system_proxy),
                checked = systemProxyEnabled && !hasInboundAuth,
                enabled = !hasInboundAuth,
                onCheckedChange = { DataStore.systemProxy.setBlocking(it) },
            )
            TrayItem(
                label = stringResource(Res.string.copy_terminal_proxy),
                icon = painterResource(Res.drawable.content_copy),
            ) {
                scope.launch(Dispatchers.Default) {
                    setSystemClipboardPlainText(currentProxyEnvCommand())
                }
            }
            TrayItem(
                label = stringResource(Res.string.exit),
                accessKey = TrayKey.X,
                icon = painterResource(Res.drawable.close),
                onClick = onExit,
            )
        },
    )
}
