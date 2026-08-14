package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.NetworkInterfaceStrategy
import fr.husi.RuleProvider
import fr.husi.compose.DurationTextField
import fr.husi.compose.IconMaskColors
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.contentOrUnset
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.auto
import fr.husi.resources.construction
import fr.husi.resources.custom_rule_provider
import fr.husi.resources.disable_tcp_keep_alive
import fr.husi.resources.ecg
import fr.husi.resources.fallback
import fr.husi.resources.hourglass_top
import fr.husi.resources.hybrid
import fr.husi.resources.import_contacts
import fr.husi.resources.ipv4_only
import fr.husi.resources.ipv6_only
import fr.husi.resources.keep_default
import fr.husi.resources.network_interface_preference
import fr.husi.resources.network_interface_strategy
import fr.husi.resources.network_strategy
import fr.husi.resources.not_set
import fr.husi.resources.prefer_ipv4
import fr.husi.resources.prefer_ipv6
import fr.husi.resources.public_icon
import fr.husi.resources.route_rules_official
import fr.husi.resources.route_rules_provider
import fr.husi.resources.router
import fr.husi.resources.rule_folder
import fr.husi.resources.tcp_keep_alive_idle
import fr.husi.resources.tcp_keep_alive_interval
import fr.husi.resources.timer
import fr.husi.ui.PlatformRouteOptions
import fr.husi.ui.ProxyAppsPreferences
import fr.husi.ui.StringOrRes
import fr.husi.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RouteSettingsGroup(
    needReload: () -> Unit,
    openAppManager: () -> Unit,
) {
    val serviceModeState by DataStore.configurationStore
        .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
        .collectAsStateWithLifecycle(Key.MODE_VPN)

    ProxyAppsPreferences(openAppManager)

    PlatformRouteOptions(
        needReload = needReload,
        isVpnMode = serviceModeState == Key.MODE_VPN,
    )
    PreferenceDivider()

    fun networkStrategyTextRes(value: String): StringResource = when (value) {
        "" -> Res.string.auto
        "prefer_ipv6" -> Res.string.prefer_ipv6
        "prefer_ipv4" -> Res.string.prefer_ipv4
        "ipv4_only" -> Res.string.ipv4_only
        "ipv6_only" -> Res.string.ipv6_only
        else -> Res.string.auto
    }

    val networkStrategyValue by DataStore.configurationStore
        .stringFlow(Key.NETWORK_STRATEGY, "")
        .collectAsStateWithLifecycle("")
    ListPreference(
        value = networkStrategyValue,
        onValueChange = {
            DataStore.networkStrategy = it
            needReload()
        },
        values = listOf("", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only"),
        title = { Text(stringResource(Res.string.network_strategy)) },
        icon = {
            MaskedIcon(Res.drawable.router, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(stringResource(networkStrategyTextRes(networkStrategyValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(networkStrategyTextRes(it))) },
    )
    PreferenceDivider()

    fun networkInterfaceStrategyTextRes(selection: Int): StringResource = when (selection) {
        NetworkInterfaceStrategy.DEFAULT -> Res.string.keep_default
        NetworkInterfaceStrategy.HYBRID -> Res.string.hybrid
        NetworkInterfaceStrategy.FALLBACK -> Res.string.fallback
        else -> Res.string.keep_default
    }

    val networkInterfaceValue by DataStore.configurationStore
        .intFlow(Key.NETWORK_INTERFACE_STRATEGY, NetworkInterfaceStrategy.DEFAULT)
        .collectAsStateWithLifecycle(NetworkInterfaceStrategy.DEFAULT)
    ListPreference(
        value = networkInterfaceValue,
        onValueChange = {
            DataStore.networkInterfaceType = it
            needReload()
        },
        values = listOf(
            NetworkInterfaceStrategy.DEFAULT,
            NetworkInterfaceStrategy.HYBRID,
            NetworkInterfaceStrategy.FALLBACK,
        ),
        title = { Text(stringResource(Res.string.network_interface_strategy)) },
        icon = {
            MaskedIcon(
                Res.drawable.construction,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(stringResource(networkInterfaceStrategyTextRes(networkInterfaceValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(networkInterfaceStrategyTextRes(it))) },
    )
    PreferenceDivider()

    val preferredInterfaces by DataStore.configurationStore
        .stringSetFlow(Key.NETWORK_PREFERRED_INTERFACES, emptySet())
        .collectAsStateWithLifecycle(emptySet())
    MultiSelectListPreference(
        value = preferredInterfaces,
        onValueChange = {
            DataStore.networkPreferredInterfaces = it
            needReload()
        },
        values = listOf("wifi", "cellular", "ethernet", "other"),
        title = { Text(stringResource(Res.string.network_interface_preference)) },
        icon = {
            MaskedIcon(
                Res.drawable.public_icon,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = {
            val text = if (preferredInterfaces.isEmpty()) {
                stringResource(Res.string.not_set)
            } else preferredInterfaces.joinToString("\n")
            Text(text)
        },
        valueToText = { AnnotatedString(it) },
    )
    PreferenceDivider()

    val defaultDisableTcpKeepAlive = PlatformInfo.isAndroid
    val disableTcpKeepAliveValue by DataStore.configurationStore
        .booleanFlow(Key.DISABLE_TCP_KEEP_ALIVE, defaultDisableTcpKeepAlive)
        .collectAsStateWithLifecycle(defaultDisableTcpKeepAlive)
    SwitchPreference(
        value = disableTcpKeepAliveValue,
        onValueChange = {
            DataStore.disableTcpKeepAlive = it
            needReload()
        },
        title = { Text(stringResource(Res.string.disable_tcp_keep_alive)) },
        icon = {
            MaskedIcon(Res.drawable.ecg, color = IconMaskColors.IconLightGreen)
        },
    )
    PreferenceDivider()

    val tcpKeepAliveIdleValue by DataStore.configurationStore
        .stringFlow(Key.TCP_KEEP_ALIVE_IDLE, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = tcpKeepAliveIdleValue,
        onValueChange = {
            DataStore.tcpKeepAliveIdle = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tcp_keep_alive_idle)) },
        textToValue = { it },
        enabled = !disableTcpKeepAliveValue,
        icon = {
            MaskedIcon(
                Res.drawable.hourglass_top,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(tcpKeepAliveIdleValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        DurationTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val tcpKeepAliveIntervalValue by DataStore.configurationStore
        .stringFlow(Key.TCP_KEEP_ALIVE_INTERVAL_0, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = tcpKeepAliveIntervalValue,
        onValueChange = {
            DataStore.tcpKeepAliveInterval = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tcp_keep_alive_interval)) },
        textToValue = { it },
        enabled = !disableTcpKeepAliveValue,
        icon = {
            MaskedIcon(Res.drawable.timer, color = IconMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(tcpKeepAliveIntervalValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        DurationTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    fun rulesProviderText(index: Int): StringOrRes = when (index) {
        RuleProvider.OFFICIAL -> StringOrRes.Res(Res.string.route_rules_official)
        RuleProvider.LOYALSOLDIER -> StringOrRes.Direct("Loyalsoldier (1715173329/sing-geo*)")
        RuleProvider.CHOCOLATE4U -> StringOrRes.Direct("Chocolate4U/Iran-sing-box-rules")
        RuleProvider.RUNETFREEDOM -> StringOrRes.Direct("runetfreedom/russia-v2ray-rules-dat")
        RuleProvider.CUSTOM -> StringOrRes.Res(Res.string.custom_rule_provider)
        else -> StringOrRes.Res(Res.string.route_rules_official)
    }

    val rulesProviderValue by DataStore.configurationStore
        .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
        .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)
    ListPreference(
        value = rulesProviderValue,
        onValueChange = { DataStore.rulesProvider = it },
        values = listOf(
            RuleProvider.OFFICIAL,
            RuleProvider.LOYALSOLDIER,
            RuleProvider.CHOCOLATE4U,
            RuleProvider.RUNETFREEDOM,
            RuleProvider.CUSTOM,
        ),
        title = { Text(stringResource(Res.string.route_rules_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.rule_folder,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(rulesProviderText(rulesProviderValue))) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(stringOrRes(rulesProviderText(it))) },
    )
    if (rulesProviderValue == RuleProvider.CUSTOM) {
        PreferenceDivider()
        val defaultUrl =
            "https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set"
        val customRuleProviderValue by DataStore.configurationStore
            .stringFlow(Key.CUSTOM_RULE_PROVIDER, defaultUrl)
            .collectAsStateWithLifecycle(defaultUrl)
        TextFieldPreference(
            value = customRuleProviderValue,
            onValueChange = { DataStore.customRuleProvider = it },
            title = { Text(stringResource(Res.string.custom_rule_provider)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.import_contacts,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(customRuleProviderValue)) },
            valueToText = { it },
        ) { value, onValueChange, onOk ->
            LinkOrContentTextField(value, onValueChange, onOk)
        }
    }
}
