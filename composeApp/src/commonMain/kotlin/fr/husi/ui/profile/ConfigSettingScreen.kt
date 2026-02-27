package fr.husi.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BackHandler
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.apply
import fr.husi.resources.cancel
import fr.husi.resources.close
import fr.husi.resources.custom_config
import fr.husi.resources.delete
import fr.husi.resources.delete_confirm_prompt
import fr.husi.resources.done
import fr.husi.resources.emoji_symbols
import fr.husi.resources.is_outbound_only
import fr.husi.resources.layers
import fr.husi.resources.lines
import fr.husi.resources.no
import fr.husi.resources.not_set
import fr.husi.resources.ok
import fr.husi.resources.outbond
import fr.husi.resources.profile_name
import fr.husi.resources.question_mark
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.warning
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSettingScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: ConfigSettingsViewModel = viewModel { ConfigSettingsViewModel() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()

    var showBackAlert by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    BackHandler(enabled = isDirty) {
        showBackAlert = true
    }

    val config = when (uiState.type) {
        fr.husi.fmt.config.ConfigBean.TYPE_CONFIG -> uiState.customConfig
        fr.husi.fmt.config.ConfigBean.TYPE_OUTBOUND -> uiState.customOutbound
        else -> error("impossible")
    }

    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.custom_config)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            onResult(false)
                        }
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                            onClick = { showDeleteAlert = true },
                        )
                    }
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.done),
                        contentDescription = stringResource(Res.string.apply),
                    ) {
                        viewModel.save()
                        onResult(true)
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        ProvidePreferenceLocals {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(innerPadding),
                ) {
                    item("name") {
                        TextFieldPreference(
                            value = uiState.name,
                            onValueChange = { viewModel.setName(it) },
                            title = { Text(stringResource(Res.string.profile_name)) },
                            textToValue = { it },
                            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
                            summary = { Text(contentOrUnset(uiState.name)) },
                            valueToText = { it },
                        )
                    }
                    item("outbound_only") {
                        SwitchPreference(
                            value = uiState.type == fr.husi.fmt.config.ConfigBean.TYPE_OUTBOUND,
                            onValueChange = {
                                viewModel.setType(
                                    if (it) {
                                        fr.husi.fmt.config.ConfigBean.TYPE_OUTBOUND
                                    } else {
                                        fr.husi.fmt.config.ConfigBean.TYPE_CONFIG
                                    },
                                )
                            },
                            title = { Text(stringResource(Res.string.is_outbound_only)) },
                            icon = { Icon(vectorResource(Res.drawable.outbond), null) },
                        )
                    }
                    item("config") {
                        Preference(
                            title = { Text(stringResource(Res.string.custom_config)) },
                            icon = { Icon(vectorResource(Res.drawable.layers), null) },
                            summary = {
                                val text = if (config.isBlank()) {
                                    stringResource(Res.string.not_set)
                                } else {
                                    stringResource(Res.string.lines, config.count { it == 'n' } + 1)
                                }
                                Text(text)
                            },
                            onClick = {
                                showEditor = true
                            },
                        )
                    }
                    item("bottom_padding") {
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    if (showEditor) {
        Dialog(
            onDismissRequest = { showEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ConfigEditDialog(
                initialText = config,
                onDismiss = { showEditor = false },
                onSave = {
                    viewModel.setConfig(it)
                    showEditor = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.save()
                    onResult(true)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    onResult(false)
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.delete()
                    onResult(true)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showDeleteAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning), null) },
            title = { Text(stringResource(Res.string.delete_confirm_prompt)) },
        )
    }
}
