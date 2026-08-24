package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.Preference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.android
import fr.husi.resources.auto
import fr.husi.resources.category
import fr.husi.resources.certificate_authority
import fr.husi.resources.clear_remembered_authentication_answers
import fr.husi.resources.client_certificate
import fr.husi.resources.client_key
import fr.husi.resources.delete
import fr.husi.resources.dns
import fr.husi.resources.domain
import fr.husi.resources.emoji_symbols
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.fingerprint
import fr.husi.resources.insecure
import fr.husi.resources.language
import fr.husi.resources.local_hostname
import fr.husi.resources.lock
import fr.husi.resources.machine_certificate
import fr.husi.resources.machine_key
import fr.husi.resources.openconnect_allow_insecure_crypto
import fr.husi.resources.openconnect_auth_group
import fr.husi.resources.openconnect_authentication
import fr.husi.resources.openconnect_flavor
import fr.husi.resources.openconnect_reported_os
import fr.husi.resources.password
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.router
import fr.husi.resources.security_settings
import fr.husi.resources.tls_peer_fingerprint
import fr.husi.resources.tls_server_name
import fr.husi.resources.user_agent
import fr.husi.resources.username
import fr.husi.resources.vpn_key
import fr.husi.resources.vpn_server_url
import fr.husi.resources.warning_amber
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
fun OpenConnectSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel = profileEditorViewModel(profileId, isSubscription) {
        OpenConnectSettingsViewModel()
    }
    ProfileSettingsScreenScaffold(
        Res.string.profile_config,
        viewModel,
        onResult,
        onOpenConfigEditor,
    ) { state, _ ->
        openConnectSettings(state as OpenConnectUiState, viewModel)
    }
}

private fun LazyListScope.openConnectSettings(
    state: OpenConnectUiState,
    viewModel: OpenConnectSettingsViewModel,
) {
    val flavors = listOf(
        "",
        "anyconnect",
        "gp",
        "fortinet",
        "f5",
        "pulse",
        "nc",
    )
    val pulseReportedOS = listOf(
        "",
        "linux",
        "linux-64",
        "win",
        "mac-intel",
        "android",
        "apple-ios",
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
    if (state.formEntries.isNotEmpty()) {
        item("category_openconnect_authentication") {
            PreferenceCategory(text = { Text(stringResource(Res.string.openconnect_authentication)) })
        }
        preferenceGroup {
            Preference(
                title = { Text(stringResource(Res.string.clear_remembered_authentication_answers)) },
                icon = { MaskedIcon(Res.drawable.delete, IconMaskColors.IconCoral) },
                onClick = viewModel::clearFormEntries,
            )
        }
    }
    item("category_proxy") { PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) }) }
    preferenceGroup {
        TextFieldPreference(
            value = state.server,
            onValueChange = viewModel::setServer,
            title = { Text(stringResource(Res.string.vpn_server_url)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.server)) },
            icon = { MaskedIcon(Res.drawable.router, IconMaskColors.IconLightBlue) },
        )
        ListPreference(
            value = state.flavor,
            onValueChange = viewModel::setFlavor,
            values = flavors,
            title = { Text(stringResource(Res.string.openconnect_flavor)) },
            summary = {
                Text(state.flavor.ifBlank { stringResource(Res.string.auto) })
            },
            icon = { MaskedIcon(Res.drawable.category, IconMaskColors.IconLavender) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it.ifBlank { stringResource(Res.string.auto) }) },
        )
        if (state.flavor == "pulse") {
            ListPreference(
                value = state.reportedOS,
                onValueChange = viewModel::setReportedOS,
                values = pulseReportedOS,
                title = { Text(stringResource(Res.string.openconnect_reported_os)) },
                summary = {
                    Text(state.reportedOS.ifBlank { stringResource(Res.string.auto) })
                },
                icon = { MaskedIcon(Res.drawable.android, IconMaskColors.IconLightYellow) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it.ifBlank { stringResource(Res.string.auto) }) },
            )
        }
        TextFieldPreference(
            value = state.username,
            onValueChange = viewModel::setUsername,
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.username)) },
            icon = { MaskedIcon(Res.drawable.person, IconMaskColors.IconLightGreen) },
        )
        PasswordPreference(
            value = state.password,
            onValueChange = viewModel::setPassword,
            title = { Text(stringResource(Res.string.password)) },
        )
        TextFieldPreference(
            value = state.authGroup,
            onValueChange = viewModel::setAuthGroup,
            title = { Text(stringResource(Res.string.openconnect_auth_group)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.authGroup)) },
            icon = { MaskedIcon(Res.drawable.domain, IconMaskColors.IconLightOrange) },
        )
        TextFieldPreference(
            value = state.userAgent,
            onValueChange = viewModel::setUserAgent,
            title = { Text(stringResource(Res.string.user_agent)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.userAgent)) },
            icon = { MaskedIcon(Res.drawable.language, IconMaskColors.IconWarmGray) },
        )
        TextFieldPreference(
            value = state.localHostname,
            onValueChange = viewModel::setLocalHostname,
            title = { Text(stringResource(Res.string.local_hostname)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.localHostname)) },
            icon = { MaskedIcon(Res.drawable.dns, IconMaskColors.IconWarmGray) },
        )
    }
    item("category_tls") { PreferenceCategory(text = { Text(stringResource(Res.string.security_settings)) }) }
    preferenceGroup {
        SwitchPreference(
            value = state.tlsInsecure,
            onValueChange = viewModel::setTlsInsecure,
            title = { Text(stringResource(Res.string.insecure)) },
            icon = {
                MaskedIcon(
                    Res.drawable.warning_amber,
                    IconMaskColors.IconCoral,
                    IconMaskShapes.risk(),
                )
            },
        )
        TextFieldPreference(
            value = state.tlsServerName,
            onValueChange = viewModel::setTlsServerName,
            title = { Text(stringResource(Res.string.tls_server_name)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.tlsServerName)) },
            icon = { MaskedIcon(Res.drawable.dns, IconMaskColors.IconLightBlue) },
        )
        TextFieldPreference(
            value = state.tlsPeerFingerprint,
            onValueChange = viewModel::setTlsPeerFingerprint,
            title = { Text(stringResource(Res.string.tls_peer_fingerprint)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.tlsPeerFingerprint)) },
            icon = { MaskedIcon(Res.drawable.fingerprint, IconMaskColors.IconLightYellow) },
        )
        TextFieldPreference(
            value = state.certificateAuthority,
            onValueChange = viewModel::setCertificateAuthority,
            title = { Text(stringResource(Res.string.certificate_authority)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.certificateAuthority)) },
            textField = { fieldValue, change, ok ->
                MultilineTextField(fieldValue, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.enhanced_encryption, IconMaskColors.IconLightBlue) },
        )
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
        PasswordPreference(
            value = state.clientKeyPassword,
            onValueChange = viewModel::setClientKeyPassword,
        )
        TextFieldPreference(
            value = state.mcaCertificate,
            onValueChange = viewModel::setMcaCertificate,
            title = { Text(stringResource(Res.string.machine_certificate)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.mcaCertificate)) },
            textField = { fieldValue, change, ok ->
                MultilineTextField(fieldValue, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.lock, IconMaskColors.IconLightOrange, IconMaskShapes.credential()) },
        )
        TextFieldPreference(
            value = state.mcaKey,
            onValueChange = viewModel::setMcaKey,
            title = { Text(stringResource(Res.string.machine_key)) },
            textToValue = { it },
            valueToText = { it },
            summary = { Text(contentOrUnset(state.mcaKey)) },
            textField = { fieldValue, change, ok ->
                MultilineTextField(fieldValue, change, ok)
            },
            icon = { MaskedIcon(Res.drawable.vpn_key, IconMaskColors.IconLightPink, IconMaskShapes.credential()) },
        )
        PasswordPreference(
            value = state.mcaKeyPassword,
            onValueChange = viewModel::setMcaKeyPassword,
        )
        SwitchPreference(
            value = state.allowInsecureCrypto,
            onValueChange = viewModel::setAllowInsecureCrypto,
            title = { Text(stringResource(Res.string.openconnect_allow_insecure_crypto)) },
            icon = {
                MaskedIcon(
                    Res.drawable.warning_amber,
                    IconMaskColors.IconCoral,
                    IconMaskShapes.risk(),
                )
            },
        )
    }
}
