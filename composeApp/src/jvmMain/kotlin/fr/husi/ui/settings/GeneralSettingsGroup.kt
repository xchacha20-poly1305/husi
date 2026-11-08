package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.SliderPreference
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.collectAdvancedFeatureAsStateWithLifecycle
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.database.preference.setBlocking
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.logLevelString
import fr.husi.repository.LocalRepository
import fr.husi.resources.Res
import fr.husi.resources.allow_apps_bypass_vpn
import fr.husi.resources.always_show_address
import fr.husi.resources.always_show_address_sum
import fr.husi.resources.blurred_address
import fr.husi.resources.bug_report
import fr.husi.resources.center_focus_weak
import fr.husi.resources.description
import fr.husi.resources.developer_board
import fr.husi.resources.developer_mode
import fr.husi.resources.insecure_warn
import fr.husi.resources.log_level
import fr.husi.resources.max_log_line
import fr.husi.resources.mtu
import fr.husi.resources.profile_traffic_statistics
import fr.husi.resources.profile_traffic_statistics_summary
import fr.husi.resources.public_icon
import fr.husi.resources.security
import fr.husi.resources.service_mode
import fr.husi.resources.service_mode_proxy
import fr.husi.resources.service_mode_vpn
import fr.husi.resources.show_direct_speed
import fr.husi.resources.show_direct_speed_sum
import fr.husi.resources.speed
import fr.husi.resources.traffic
import fr.husi.resources.transform
import fr.husi.resources.transgender
import fr.husi.ui.AutoConnectPreference
import fr.husi.ui.MeteredNetworkPreference
import fr.husi.ui.PlatformGeneralOptions
import fr.husi.ui.PlatformSecurityOptions
import fr.husi.ui.rememberAppLanguageController
import fr.husi.ui.rememberApplyNightMode
import fr.husi.ui.rememberThemeExtraColors
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun GeneralSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val repository = koinInject<LocalRepository>()
    val applyNightMode = rememberApplyNightMode()
    val debugListenFeature by collectAdvancedFeatureAsStateWithLifecycle(Key.DEBUG_LISTEN)

    AutoConnectPreference(showMessage = showMessage)

    val appTheme by DataStore.appTheme.collectAsStateWithLifecycle()
    val nightTheme by DataStore.nightTheme.collectAsStateWithLifecycle()
    val languageController = rememberAppLanguageController(defaultTag = "")
    val appLanguage by languageController.flow.collectAsStateWithLifecycle(languageController.value)
    AppearanceSettingsGroup(
        appTheme = appTheme,
        onAppThemeChange = { DataStore.appTheme.setBlocking(it) },
        extraColors = rememberThemeExtraColors(),
        nightTheme = nightTheme,
        onNightThemeChange = {
            DataStore.nightTheme.setBlocking(it)
            applyNightMode(it)
        },
        appLanguage = appLanguage,
        onAppLanguageChange = { languageController.value = it },
    )

    fun serviceModeText(mode: String): StringResource = when (mode) {
        Key.MODE_VPN -> Res.string.service_mode_vpn
        Key.MODE_PROXY -> Res.string.service_mode_proxy
        else -> Res.string.service_mode_vpn
    }

    val serviceModeValue by DataStore.serviceMode.collectAsStateWithLifecycle()
    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    ListPreference(
        value = serviceModeValue,
        onValueChange = { mode ->
            DataStore.serviceMode.setBlocking(mode)
            if (serviceStatus.state.canStop) {
                repository.reloadService()
            }
        },
        values = listOf(Key.MODE_VPN, Key.MODE_PROXY),
        title = { Text(stringResource(Res.string.service_mode)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_mode,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(serviceModeText(serviceModeValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(serviceModeText(it))) },
    )

    val mtuValue by DataStore.mtu.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = mtuValue,
        onValueChange = {
            DataStore.mtu.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.mtu)) },
        textToValue = { it.toIntOrNull() ?: 9000 },
        icon = {
            MaskedIcon(
                Res.drawable.public_icon,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(mtuValue.toString()) },
        valueToText = { it.toString() },
    )
    val bypassValue by DataStore.allowAppsBypassVpn.collectAsStateWithLifecycle()
    SwitchPreference(
        value = bypassValue,
        onValueChange = {
            DataStore.allowAppsBypassVpn.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_apps_bypass_vpn)) },
        icon = {
            MaskedIcon(Res.drawable.transform, color = IconMaskColors.IconCyan)
        },
    )
    PlatformGeneralOptions(needReload)

    val speedIntervalValue by DataStore.speedInterval.collectAsStateWithLifecycle()
    SpeedIntervalPreference(
        value = speedIntervalValue,
        onValueChange = { DataStore.speedInterval.setBlocking(it) },
    )

    val profileTrafficStatisticsValue by DataStore.profileTrafficStatistics.collectAsStateWithLifecycle()
    SwitchPreference(
        value = profileTrafficStatisticsValue,
        onValueChange = { DataStore.profileTrafficStatistics.setBlocking(it) },
        title = { Text(stringResource(Res.string.profile_traffic_statistics)) },
        icon = {
            MaskedIcon(
                Res.drawable.traffic,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringResource(Res.string.profile_traffic_statistics_summary)) },
        enabled = speedIntervalValue != 0,
    )

    val showDirectSpeedValue by DataStore.showDirectSpeed.collectAsStateWithLifecycle()
    SwitchPreference(
        value = showDirectSpeedValue,
        onValueChange = { DataStore.showDirectSpeed.setBlocking(it) },
        title = { Text(stringResource(Res.string.show_direct_speed)) },
        icon = {
            MaskedIcon(Res.drawable.speed, color = IconMaskColors.IconLightPink)
        },
        summary = { Text(stringResource(Res.string.show_direct_speed_sum)) },
        enabled = speedIntervalValue != 0,
    )

    val alwaysShowAddressValue by DataStore.alwaysShowAddress.collectAsStateWithLifecycle()
    SwitchPreference(
        value = alwaysShowAddressValue,
        onValueChange = { DataStore.alwaysShowAddress.setBlocking(it) },
        title = { Text(stringResource(Res.string.always_show_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.center_focus_weak,
                color = IconMaskColors.IconCoral,
            )
        },
        summary = { Text(stringResource(Res.string.always_show_address_sum)) },
    )

    val blurredAddressValue by DataStore.blurredAddress.collectAsStateWithLifecycle()
    SwitchPreference(
        value = blurredAddressValue,
        onValueChange = { DataStore.blurredAddress.setBlocking(it) },
        title = { Text(stringResource(Res.string.blurred_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.transgender,
                color = IconMaskColors.IconLavender,
            )
        },
        enabled = alwaysShowAddressValue,
    )

    val securityAdvisoryValue by DataStore.securityAdvisory.collectAsStateWithLifecycle()
    SwitchPreference(
        value = securityAdvisoryValue,
        onValueChange = { DataStore.securityAdvisory.setBlocking(it) },
        title = { Text(stringResource(Res.string.insecure_warn)) },
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
    )
    PlatformSecurityOptions()
    MeteredNetworkPreference(needReload)

    val logLevelValue by DataStore.logLevel.collectAsStateWithLifecycle()
    ListPreference(
        value = logLevelValue,
        onValueChange = {
            DataStore.logLevel.setBlocking(it)
            needRestart()
        },
        values = intListN(7),
        title = { Text(stringResource(Res.string.log_level)) },
        icon = {
            MaskedIcon(
                Res.drawable.bug_report,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(logLevelString(logLevelValue)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(logLevelString(it)) },
    )

    val maxLogLineValue by DataStore.logMaxLine.collectAsStateWithLifecycle()
    var previewValue by remember { mutableFloatStateOf(maxLogLineValue.toFloat()) }
    SliderPreference(
        value = maxLogLineValue.toFloat(),
        onValueChange = { DataStore.logMaxLine.setBlocking(it.toInt()) },
        sliderValue = previewValue,
        onSliderValueChange = { previewValue = it },
        title = { Text(stringResource(Res.string.max_log_line)) },
        valueRange = 1024f..1024f * 64f,
        valueSteps = 128,
        icon = {
            MaskedIcon(
                Res.drawable.description,
                color = IconMaskColors.IconWarmGray,
            )
        },
        valueText = { Text(previewValue.toInt().toString()) },
    )

    val logTerminalModeValue by DataStore.logTerminalMode.collectAsStateWithLifecycle()
    LogTerminalModePreference(
        value = logTerminalModeValue,
        onValueChange = { DataStore.logTerminalMode.setBlocking(it) },
    )
    if (debugListenFeature != null) {
        val debugListenValue by DataStore.debugListen.collectAsStateWithLifecycle()
        TextFieldPreference(
            value = debugListenValue,
            onValueChange = {
                DataStore.debugListen.setBlocking(it)
                needReload()
            },
            title = { Text("pprof listen") },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.developer_board,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.risk(),
                )
            },
            summary = { Text(contentOrUnset(debugListenValue)) },
            valueToText = { it },
        )
    }
}
