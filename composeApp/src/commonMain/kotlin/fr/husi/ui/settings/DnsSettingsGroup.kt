package fr.husi.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import fr.husi.DOMAIN_STRATEGY_AUTO
import fr.husi.compose.DurationTextField
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.HostTextField
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.auto
import fr.husi.resources.direct_dns
import fr.husi.resources.dns
import fr.husi.resources.dns_hosts
import fr.husi.resources.domain_strategy_for_direct
import fr.husi.resources.domain_strategy_for_server
import fr.husi.resources.emoji_emotions
import fr.husi.resources.fake_dns
import fr.husi.resources.fake_dns_for_all
import fr.husi.resources.fake_dns_for_all_sum
import fr.husi.resources.fake_ip_range_4
import fr.husi.resources.fake_ip_range_6
import fr.husi.resources.fakedns_message
import fr.husi.resources.ipv4_only
import fr.husi.resources.ipv6_only
import fr.husi.resources.lock
import fr.husi.resources.mdns_network_interfaces
import fr.husi.resources.optimistic_cache
import fr.husi.resources.prefer_ipv4
import fr.husi.resources.prefer_ipv6
import fr.husi.resources.remote_dns
import fr.husi.resources.text_select_end
import fr.husi.resources.transform
import fr.husi.resources.wifi
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DnsSettingsGroup(
    needReload: () -> Unit,
) {
    val remoteDnsValue by DataStore.remoteDns.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = remoteDnsValue,
        onValueChange = {
            DataStore.remoteDns.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.remote_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(remoteDnsValue)) },
        valueToText = { it },
    )

    val directDnsValue by DataStore.directDns.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = directDnsValue,
        onValueChange = {
            DataStore.directDns.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.direct_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(directDnsValue)) },
        valueToText = { it },
    )

    val mdnsValue by DataStore.mDNS.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = mdnsValue,
        onValueChange = {
            DataStore.mDNS.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.mdns_network_interfaces)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.wifi, color = IconMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(mdnsValue)) },
        valueToText = { it },
    )

    val optimisticCacheValue by DataStore.dnsOptimisticCache.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = optimisticCacheValue,
        onValueChange = {
            DataStore.dnsOptimisticCache.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.optimistic_cache)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.emoji_emotions,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(contentOrUnset(optimisticCacheValue)) },
        valueToText = { it },
        textField = { value, onValueChange, onOk ->
            DurationTextField(value, onValueChange, onOk)
        },
    )

    val domainStrategyDirectValue by DataStore.domainStrategyForDirect.collectAsStateWithLifecycle()
    val domainStrategyValues =
        listOf(DOMAIN_STRATEGY_AUTO, "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
    val domainStrategyEntries = listOf(
        stringResource(Res.string.auto),
        stringResource(Res.string.prefer_ipv6),
        stringResource(Res.string.prefer_ipv4),
        stringResource(Res.string.ipv4_only),
        stringResource(Res.string.ipv6_only),
    )
    ListPreference(
        value = domainStrategyDirectValue,
        onValueChange = {
            DataStore.domainStrategyForDirect.setBlocking(it)
            needReload()
        },
        values = domainStrategyValues,
        title = { Text(stringResource(Res.string.domain_strategy_for_direct)) },
        icon = { Spacer(Modifier.size(24.dp)) },
        summary = {
            val selectedIndex =
                domainStrategyValues.indexOf(domainStrategyDirectValue).takeIf { it >= 0 } ?: 0
            Text(domainStrategyEntries[selectedIndex])
        },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val selectedIndex = domainStrategyValues.indexOf(it).takeIf { index -> index >= 0 } ?: 0
            AnnotatedString(domainStrategyEntries[selectedIndex])
        },
    )

    val domainStrategyServerValue by DataStore.domainStrategyForServer.collectAsStateWithLifecycle()
    ListPreference(
        value = domainStrategyServerValue,
        onValueChange = {
            DataStore.domainStrategyForServer.setBlocking(it)
            needReload()
        },
        values = domainStrategyValues,
        title = { Text(stringResource(Res.string.domain_strategy_for_server)) },
        icon = { Spacer(Modifier.size(24.dp)) },
        summary = {
            val selectedIndex =
                domainStrategyValues.indexOf(domainStrategyServerValue).takeIf { it >= 0 } ?: 0
            Text(domainStrategyEntries[selectedIndex])
        },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val selectedIndex = domainStrategyValues.indexOf(it).takeIf { index -> index >= 0 } ?: 0
            AnnotatedString(domainStrategyEntries[selectedIndex])
        },
    )

    val enableFakeDnsValue by DataStore.enableFakeDns.collectAsStateWithLifecycle()
    SwitchPreference(
        value = enableFakeDnsValue,
        onValueChange = {
            DataStore.enableFakeDns.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_dns)) },
        icon = {
            MaskedIcon(Res.drawable.lock, color = IconMaskColors.IconLightPink)
        },
        summary = { Text(stringResource(Res.string.fakedns_message)) },
    )

    val fakeDnsForAllValue by DataStore.fakeDNSForAll.collectAsStateWithLifecycle()
    SwitchPreference(
        value = fakeDnsForAllValue,
        onValueChange = {
            DataStore.fakeDNSForAll.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_dns_for_all)) },
        enabled = enableFakeDnsValue,
        icon = {
            MaskedIcon(
                Res.drawable.lock,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.fake_dns_for_all_sum)) },
    )

    val fakeDnsRange4Value by DataStore.fakeDNSRange4.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = fakeDnsRange4Value,
        onValueChange = {
            DataStore.fakeDNSRange4.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_ip_range_4)) },
        textToValue = { it },
        enabled = enableFakeDnsValue,
        icon = {
            MaskedIcon(
                Res.drawable.text_select_end,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(fakeDnsRange4Value)) },
        valueToText = { it },
    )

    val fakeDnsRange6Value by DataStore.fakeDNSRange6.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = fakeDnsRange6Value,
        onValueChange = {
            DataStore.fakeDNSRange6.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_ip_range_6)) },
        textToValue = { it },
        enabled = enableFakeDnsValue,
        icon = {
            MaskedIcon(
                Res.drawable.text_select_end,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(fakeDnsRange6Value)) },
        valueToText = { it },
    )

    val dnsHostsValue by DataStore.dnsHosts.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = dnsHostsValue,
        onValueChange = {
            DataStore.dnsHosts.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.dns_hosts)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.transform, color = IconMaskColors.IconCyan)
        },
        summary = { Text(contentOrUnset(dnsHostsValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        HostTextField(value, onValueChange, onOk)
    }
}
