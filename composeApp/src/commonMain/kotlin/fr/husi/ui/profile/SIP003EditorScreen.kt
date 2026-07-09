package fr.husi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BackHandler
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.PreferenceShapes
import fr.husi.compose.PreferenceType
import fr.husi.compose.ProfilePreferenceIcon
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.apply
import fr.husi.resources.assistant_direction
import fr.husi.resources.certificates
import fr.husi.resources.close
import fr.husi.resources.done
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.http_host
import fr.husi.resources.http_path
import fr.husi.resources.multiple_stop
import fr.husi.resources.mux_number
import fr.husi.resources.no
import fr.husi.resources.numbers
import fr.husi.resources.obfs_mode
import fr.husi.resources.ok
import fr.husi.resources.plugin
import fr.husi.resources.question_mark
import fr.husi.resources.router
import fr.husi.resources.security
import fr.husi.resources.sip003_editor
import fr.husi.resources.sip003_pick_plugin_first
import fr.husi.resources.tls
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.v2ray_transport
import fr.husi.resources.vpn_key
import fr.husi.results.LocalResultEventBus
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SIP003EditorScreen(
    pluginName: String,
    initialOpts: String,
    resultKey: String,
    onBack: () -> Unit,
) {
    val viewModel: SIP003EditorViewModel = viewModel {
        SIP003EditorViewModel(pluginName, initialOpts)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val resultBus = LocalResultEventBus.current

    var showBackAlert by remember { mutableStateOf(false) }

    val saveAndExit: () -> Unit = {
        resultBus.sendResult<String?>(resultKey, viewModel.serialize())
        onBack()
    }
    val discardAndExit: () -> Unit = {
        resultBus.sendResult<String?>(resultKey, null)
        onBack()
    }
    val confirmBack: () -> Unit = {
        if (isDirty) {
            showBackAlert = true
        } else {
            discardAndExit()
        }
    }

    BackHandler(enabled = isDirty) {
        showBackAlert = true
    }

    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CapsuleTopBar(
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                        onClick = confirmBack,
                    )
                },
                title = { Text(stringResource(Res.string.sip003_editor)) },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.done),
                            contentDescription = stringResource(Res.string.apply),
                            onClick = saveAndExit,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            when (pluginName) {
                SIP003_OBFS_LOCAL -> ObfsLocalForm(uiState, viewModel, innerPadding)
                SIP003_V2RAY_PLUGIN -> V2RayPluginForm(uiState, viewModel, innerPadding)
                else -> EmptyForm(innerPadding)
            }
        }
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    saveAndExit()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    discardAndExit()
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }
}

@Composable
private fun ObfsLocalForm(
    uiState: SIP003EditorUiState,
    viewModel: SIP003EditorViewModel,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        item("category", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.plugin)) })
        }
        preferenceGroup {
            ListPreference(
                value = uiState.obfs,
                values = ObfsMode.entries,
                onValueChange = viewModel::setObfs,
                title = { Text(stringResource(Res.string.obfs_mode)) },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.enhanced_encryption,
                        color = PreferenceMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(uiState.obfs.value) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it.value) },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.obfsHost,
                onValueChange = viewModel::setObfsHost,
                title = { Text(stringResource(Res.string.http_host)) },
                textToValue = { it },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.router,
                        color = PreferenceMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(uiState.obfsHost)) },
                valueToText = { it },
            )
        }
        item("bottom", "padding") {
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun V2RayPluginForm(
    uiState: SIP003EditorUiState,
    viewModel: SIP003EditorViewModel,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        item("category", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.plugin)) })
        }
        preferenceGroup {
            SwitchPreference(
                value = uiState.tls,
                onValueChange = viewModel::setTls,
                title = { Text(stringResource(Res.string.tls)) },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.security,
                        color = PreferenceMaskColors.IconCoral,
                    )
                },
            )
            PreferenceDivider()
            ListPreference(
                value = uiState.mode,
                values = V2RayMode.entries,
                onValueChange = viewModel::setMode,
                title = { Text(stringResource(Res.string.v2ray_transport)) },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.multiple_stop,
                        color = PreferenceMaskColors.IconLightGreen,
                    )
                },
                summary = { Text(uiState.mode.value) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it.value) },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.host,
                onValueChange = viewModel::setHost,
                title = { Text(stringResource(Res.string.http_host)) },
                textToValue = { it },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.router,
                        color = PreferenceMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(uiState.host)) },
                valueToText = { it },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.path,
                onValueChange = viewModel::setPath,
                title = { Text(stringResource(Res.string.http_path)) },
                textToValue = { it },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.assistant_direction,
                        color = PreferenceMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(contentOrUnset(uiState.path)) },
                valueToText = { it },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.mux,
                onValueChange = viewModel::setMux,
                title = { Text(stringResource(Res.string.mux_number)) },
                textToValue = { it.toIntOrNull() ?: DEFAULT_V2RAY_MUX },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.numbers,
                        color = PreferenceMaskColors.IconLavender,
                    )
                },
                summary = { Text(uiState.mux.toString()) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.certRaw,
                onValueChange = viewModel::setCertRaw,
                title = { Text(stringResource(Res.string.certificates)) },
                textToValue = { it },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.vpn_key,
                        color = PreferenceMaskColors.IconWarmGray,
                        shape = PreferenceShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.certRaw)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
        item("bottom", "padding") {
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun EmptyForm(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Res.string.sip003_pick_plugin_first))
    }
}
