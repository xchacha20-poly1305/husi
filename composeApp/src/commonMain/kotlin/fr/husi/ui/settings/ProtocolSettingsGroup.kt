package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.ProtocolProvider
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.download
import fr.husi.resources.file_upload
import fr.husi.resources.flight_takeoff
import fr.husi.resources.hysteria2_provider
import fr.husi.resources.hysteria_download_mbps
import fr.husi.resources.hysteria_upload_mbps
import fr.husi.resources.juicity_provider
import fr.husi.resources.plugin
import fr.husi.resources.provider_naive
import fr.husi.ui.StringOrRes
import fr.husi.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProtocolSettingsGroup(
    needReload: () -> Unit,
) {
    val uploadSpeedValue by DataStore.configurationStore
        .intFlow(Key.UPLOAD_SPEED, 0)
        .collectAsStateWithLifecycle(0)
    TextFieldPreference(
        value = uploadSpeedValue,
        onValueChange = {
            DataStore.uploadSpeed = it
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_upload_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(
                Res.drawable.file_upload,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(uploadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val downloadSpeedValue by DataStore.configurationStore
        .intFlow(Key.DOWNLOAD_SPEED, 0)
        .collectAsStateWithLifecycle(0)
    TextFieldPreference(
        value = downloadSpeedValue,
        onValueChange = {
            DataStore.downloadSpeed = it
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_download_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(Res.drawable.download, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(downloadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    fun pluginProviderText(index: Int): StringOrRes = when (index) {
        ProtocolProvider.CORE -> StringOrRes.Direct("sing-box")
        ProtocolProvider.PLUGIN -> StringOrRes.Res(Res.string.plugin)
        else -> StringOrRes.Direct("sing-box")
    }

    val hysteria2ProviderValue by DataStore.configurationStore
        .intFlow(Key.PROVIDER_HYSTERIA2, ProtocolProvider.CORE)
        .collectAsStateWithLifecycle(ProtocolProvider.CORE)
    ListPreference(
        value = hysteria2ProviderValue,
        onValueChange = {
            DataStore.providerHysteria2 = it
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.hysteria2_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(hysteria2ProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
    PreferenceDivider()

    val juicityProviderValue by DataStore.configurationStore
        .intFlow(Key.PROVIDER_JUICITY, ProtocolProvider.PLUGIN)
        .collectAsStateWithLifecycle(ProtocolProvider.PLUGIN)
    ListPreference(
        value = juicityProviderValue,
        onValueChange = {
            DataStore.providerJuicity = it
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.juicity_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(juicityProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
    PreferenceDivider()

    val naiveProviderValue by DataStore.configurationStore
        .intFlow(Key.PROVIDER_NAIVE, ProtocolProvider.CORE)
        .collectAsStateWithLifecycle(ProtocolProvider.CORE)
    ListPreference(
        value = naiveProviderValue,
        onValueChange = {
            DataStore.providerNaive = it
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.provider_naive)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(naiveProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
}
