package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.OrderedMultiselectPreference
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.listByLineOrComma
import fr.husi.resources.Res
import fr.husi.resources.auto
import fr.husi.resources.certificates
import fr.husi.resources.cipher
import fr.husi.resources.client_certificate
import fr.husi.resources.client_key
import fr.husi.resources.compare_arrows
import fr.husi.resources.directions_boat
import fr.husi.resources.dns
import fr.husi.resources.emoji_symbols
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.fingerprint
import fr.husi.resources.layers
import fr.husi.resources.lock
import fr.husi.resources.mtu
import fr.husi.resources.network
import fr.husi.resources.openvpn_auth
import fr.husi.resources.openvpn_compression
import fr.husi.resources.openvpn_control_wrap_direction
import fr.husi.resources.openvpn_control_wrap_key
import fr.husi.resources.openvpn_control_wrap_type
import fr.husi.resources.openvpn_data_ciphers
import fr.husi.resources.openvpn_peer_fingerprint
import fr.husi.resources.openvpn_redirect_gateway
import fr.husi.resources.openvpn_remote_certificate_eku
import fr.husi.resources.openvpn_remote_certificate_ku
import fr.husi.resources.openvpn_server_name_type
import fr.husi.resources.password
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.route
import fr.husi.resources.router
import fr.husi.resources.security
import fr.husi.resources.security_settings
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.sni
import fr.husi.resources.username
import fr.husi.resources.vpn_key
import fr.husi.resources.wifi
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
fun OpenVPNSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel = profileEditorViewModel(profileId, isSubscription) {
        OpenVPNSettingsViewModel()
    }
    ProfileSettingsScreenScaffold(
        Res.string.profile_config,
        viewModel,
        onResult,
        onOpenConfigEditor,
    ) { state, _ ->
        openVPNSettings(state as OpenVPNUiState, viewModel)
    }
}

private fun LazyListScope.openVPNSettings(
    state: OpenVPNUiState,
    viewModel: OpenVPNSettingsViewModel,
) {
    val networks = listOf("tcp", "udp")
    val authentications = listOf(
        "",
        "SHA1",
        "SHA224",
        "SHA256",
        "SHA384",
        "SHA512",
        "RIPEMD160",
        "MD5",
        "NONE",
    )
    val compressions = listOf(
        "",
        "none",
        "no",
        "lz4",
        "lz4-v2",
        "stub",
        "stub-v2",
        "disabled",
        "off",
    )
    val serverNameTypes = listOf(
        "",
        "subject",
        "name",
        "name-prefix",
    )
    val remoteCertificateEKUs = listOf("", "server", "client")
    val controlWrapTypes = listOf("", "tls_auth", "tls_crypt", "tls_crypt_v2")
    val controlWrapDirections = listOf("", "server", "client")
    val dataCiphers = state.dataCiphers.listByLineOrComma()
    val supportedDataCiphers = listOf(
        "AES-256-GCM",
        "AES-128-GCM",
        "CHACHA20-POLY1305",
        "AES-256-CBC",
        "AES-192-CBC",
        "AES-128-CBC",
    )

    preferenceGroup {
        TextFieldPreference(
            value = state.name,
            onValueChange = viewModel::setName,
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.name)) },
            icon = { MaskedIcon(Res.drawable.emoji_symbols, IconMaskColors.IconCyan) },
        )
    }
    item("category_proxy") { PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) }) }
    preferenceGroup {
        TextFieldPreference(
            value = state.address,
            onValueChange = viewModel::setAddress,
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.address)) },
            icon = { MaskedIcon(Res.drawable.router, IconMaskColors.IconLightBlue) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.port,
            onValueChange = viewModel::setPort,
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 1194 },
            valueToText = Int::toString,
            summary = { Text(contentOrUnset(state.port)) },
            textField = { value, change, ok ->
                UIntegerTextField(value, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.directions_boat, IconMaskColors.IconLightOrange) },
        )
        PreferenceDivider()
        ListPreference(
            value = state.network,
            onValueChange = viewModel::setNetwork,
            values = networks,
            title = { Text(stringResource(Res.string.network)) },
            summary = { Text(state.network) },
            icon = { MaskedIcon(Res.drawable.wifi, IconMaskColors.IconLavender) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.username,
            onValueChange = viewModel::setUsername,
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.username)) },
            icon = { MaskedIcon(Res.drawable.person, IconMaskColors.IconLightGreen) },
        )
        PreferenceDivider()
        PasswordPreference(
            value = state.password,
            onValueChange = viewModel::setPassword,
            title = { Text(stringResource(Res.string.password)) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.certificate,
            onValueChange = viewModel::setCertificate,
            title = { Text(stringResource(Res.string.certificates)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.certificate)) },
            textField = { fieldValue, change, ok ->
                MultilineTextField(fieldValue, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.enhanced_encryption, IconMaskColors.IconLightBlue) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.clientCertificate,
            onValueChange = viewModel::setClientCertificate,
            title = { Text(stringResource(Res.string.client_certificate)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.clientCertificate)) },
            textField = { fieldValue, change, ok ->
                MultilineTextField(fieldValue, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.fingerprint, IconMaskColors.IconLavender, IconMaskShapes.credential()) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.clientKey,
            onValueChange = viewModel::setClientKey,
            title = { Text(stringResource(Res.string.client_key)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.clientKey)) },
            textField = { fieldValue, change, ok ->
                MultilineTextField(fieldValue, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.vpn_key, IconMaskColors.IconCoral, IconMaskShapes.credential()) },
        )
        PreferenceDivider()
        OrderedMultiselectPreference(
            selected = dataCiphers,
            values = supportedDataCiphers,
            onValueChange = { viewModel.setDataCiphers(it.joinToString("\n")) },
            title = { Text(stringResource(Res.string.openvpn_data_ciphers)) },
            summary = { Text(contentOrUnset(state.dataCiphers)) },
            icon = { MaskedIcon(Res.drawable.lock, IconMaskColors.IconLightYellow) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.cipher,
            onValueChange = viewModel::setCipher,
            title = { Text(stringResource(Res.string.cipher)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.cipher)) },
            icon = { MaskedIcon(Res.drawable.lock, IconMaskColors.IconLightYellow) },
        )
        PreferenceDivider()
        ListPreference(
            value = state.auth,
            onValueChange = viewModel::setAuth,
            values = authentications,
            title = { Text(stringResource(Res.string.openvpn_auth)) },
            summary = { Text(state.auth.ifBlank { stringResource(Res.string.auto) }) },
            icon = { MaskedIcon(Res.drawable.security, IconMaskColors.IconLightPink) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it.ifBlank { stringResource(Res.string.auto) }) },
        )
        PreferenceDivider()
        ListPreference(
            value = state.compression,
            onValueChange = viewModel::setCompression,
            values = compressions,
            title = { Text(stringResource(Res.string.openvpn_compression)) },
            summary = { Text(state.compression.ifBlank { stringResource(Res.string.auto) }) },
            icon = { MaskedIcon(Res.drawable.layers, IconMaskColors.IconWarmGray) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it.ifBlank { stringResource(Res.string.auto) }) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.mtu,
            onValueChange = viewModel::setMtu,
            title = { Text(stringResource(Res.string.mtu)) },
            textToValue = { it.toIntOrNull() ?: 1500 },
            valueToText = Int::toString,
            summary = { Text(state.mtu.toString()) },
            textField = { value, change, ok ->
                UIntegerTextField(value, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.directions_boat, IconMaskColors.IconLightOrange) },
        )
        PreferenceDivider()
        SwitchPreference(
            value = state.redirectGateway,
            onValueChange = viewModel::setRedirectGateway,
            title = { Text(stringResource(Res.string.openvpn_redirect_gateway)) },
            icon = { MaskedIcon(Res.drawable.route, IconMaskColors.IconLightBlue) },
        )
    }
    item("category_tls") { PreferenceCategory(text = { Text(stringResource(Res.string.security_settings)) }) }
    preferenceGroup {
        TextFieldPreference(
            value = state.serverName,
            onValueChange = viewModel::setServerName,
            title = { Text(stringResource(Res.string.sni)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.serverName)) },
            icon = { MaskedIcon(Res.drawable.dns, IconMaskColors.IconLightBlue) },
        )
        PreferenceDivider()
        ListPreference(
            value = state.serverNameType,
            onValueChange = viewModel::setServerNameType,
            values = serverNameTypes,
            title = { Text(stringResource(Res.string.openvpn_server_name_type)) },
            summary = {
                Text(state.serverNameType.ifBlank { stringResource(Res.string.auto) })
            },
            icon = { MaskedIcon(Res.drawable.dns, IconMaskColors.IconLavender) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                AnnotatedString(it.ifBlank { stringResource(Res.string.auto) })
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.peerFingerprint,
            onValueChange = viewModel::setPeerFingerprint,
            title = { Text(stringResource(Res.string.openvpn_peer_fingerprint)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.peerFingerprint)) },
            icon = { MaskedIcon(Res.drawable.fingerprint, IconMaskColors.IconLightYellow) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.remoteCertificateKU,
            onValueChange = viewModel::setRemoteCertificateKU,
            title = { Text(stringResource(Res.string.openvpn_remote_certificate_ku)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.remoteCertificateKU)) },
            icon = { MaskedIcon(Res.drawable.security, IconMaskColors.IconLightOrange) },
        )
        PreferenceDivider()
        ListPreference(
            value = state.remoteCertificateEKU,
            onValueChange = viewModel::setRemoteCertificateEKU,
            values = remoteCertificateEKUs,
            title = { Text(stringResource(Res.string.openvpn_remote_certificate_eku)) },
            summary = {
                Text(state.remoteCertificateEKU.ifBlank { stringResource(Res.string.auto) })
            },
            icon = { MaskedIcon(Res.drawable.enhanced_encryption, IconMaskColors.IconLightPink) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                AnnotatedString(it.ifBlank { stringResource(Res.string.auto) })
            },
        )
        PreferenceDivider()
        ListPreference(
            value = state.controlWrapType,
            onValueChange = viewModel::setControlWrapType,
            values = controlWrapTypes,
            title = { Text(stringResource(Res.string.openvpn_control_wrap_type)) },
            summary = {
                Text(state.controlWrapType.ifBlank { stringResource(Res.string.auto) })
            },
            icon = { MaskedIcon(Res.drawable.layers, IconMaskColors.IconLavender) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                AnnotatedString(it.ifBlank { stringResource(Res.string.auto) })
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.controlWrapKey,
            onValueChange = viewModel::setControlWrapKey,
            title = { Text(stringResource(Res.string.openvpn_control_wrap_key)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.controlWrapKey)) },
            textField = { fieldValue, change, ok ->
                MultilineTextField(fieldValue, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.vpn_key, IconMaskColors.IconCoral, IconMaskShapes.credential()) },
        )
        if (state.controlWrapType == "tls_auth") {
            PreferenceDivider()
            ListPreference(
                value = state.controlWrapDirection,
                onValueChange = viewModel::setControlWrapDirection,
                values = controlWrapDirections,
                title = { Text(stringResource(Res.string.openvpn_control_wrap_direction)) },
                summary = {
                    Text(state.controlWrapDirection.ifBlank { stringResource(Res.string.auto) })
                },
                icon = { MaskedIcon(Res.drawable.compare_arrows, IconMaskColors.IconLightGreen) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    AnnotatedString(it.ifBlank { stringResource(Res.string.auto) })
                },
            )
        }
    }
}
