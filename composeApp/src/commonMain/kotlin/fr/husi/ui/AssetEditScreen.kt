package fr.husi.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BackHandler
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.PreferenceType
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.withNavigation
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.apply
import fr.husi.resources.assets_settings
import fr.husi.resources.cancel
import fr.husi.resources.close
import fr.husi.resources.delete
import fr.husi.resources.delete_confirm_prompt
import fr.husi.resources.done
import fr.husi.resources.emoji_symbols
import fr.husi.resources.error_title
import fr.husi.resources.link
import fr.husi.resources.no
import fr.husi.resources.ok
import fr.husi.resources.question_mark
import fr.husi.resources.route_asset_auto_update_delay
import fr.husi.resources.route_asset_name
import fr.husi.resources.timer
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.url
import fr.husi.resources.warning
import fr.husi.resources.warning_amber
import fr.husi.results.LocalResultEventBus
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Serializable
sealed interface AssetEditResult {

    @Serializable
    data object Saved : AssetEditResult

    @Serializable
    data class Created(
        val assetName: String,
    ) : AssetEditResult

    @Serializable
    data class ShouldUpdate(
        val assetName: String,
    ) : AssetEditResult

    @Serializable
    data class Deleted(
        val assetName: String,
    ) : AssetEditResult

    @Serializable
    data object Canceled : AssetEditResult

}

@Composable
internal fun AssetEditScreen(
    assetName: String,
    resultKey: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AssetEditViewModel = viewModel { AssetEditViewModel(assetName) },
) {
    val resultBus = LocalResultEventBus.current

    val isDirty by viewModel.isDirty.collectAsState()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (isDirty) {
            showBackAlert = true
        } else {
            resultBus.sendResult<AssetEditResult>(resultKey, AssetEditResult.Canceled)
            onBack()
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var illegalNameMessage by remember { mutableStateOf<StringResource?>(null) }

    fun saveAndExit() {
        viewModel.save()
        val currentName = viewModel.uiState.value.name
        val result = when {
            viewModel.isNew -> AssetEditResult.Created(currentName)
            viewModel.shouldUpdateFromInternet -> AssetEditResult.ShouldUpdate(currentName)
            else -> AssetEditResult.Saved
        }
        resultBus.sendResult(resultKey, result)
        onBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            resultBus.sendResult<AssetEditResult>(
                                resultKey,
                                AssetEditResult.Canceled,
                            )
                            onBack()
                        }
                    }
                },
                title = { Text(stringResource(Res.string.assets_settings)) },
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                        ) {
                            val editingAssetName = viewModel.editingName
                            if (editingAssetName.isEmpty()) {
                                resultBus.sendResult<AssetEditResult>(
                                    resultKey,
                                    AssetEditResult.Canceled,
                                )
                                onBack()
                            } else {
                                showDeleteConfirm = true
                            }
                        }
                    }
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.done),
                            contentDescription = stringResource(Res.string.apply),
                        ) {
                            saveAndExit()
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(
                            scrollableState = listState,
                            fadeStart = true,
                            fadeEnd = true,
                        ),
                    contentPadding = contentPadding,
                ) {
                    assetEditSettings(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.validate(viewModel.uiState.value.name)?.let {
                        illegalNameMessage = it
                        showBackAlert = false
                        return@TextButton
                    }
                    saveAndExit()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    resultBus.sendResult<AssetEditResult>(resultKey, AssetEditResult.Canceled)
                    onBack()
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    resultBus.sendResult<AssetEditResult>(
                        resultKey,
                        AssetEditResult.Deleted(viewModel.editingName),
                    )
                    onBack()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    resultBus.sendResult<AssetEditResult>(resultKey, AssetEditResult.Canceled)
                    onBack()
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning), null) },
            title = { Text(stringResource(Res.string.delete_confirm_prompt)) },
        )
    }

    illegalNameMessage?.let { id ->
        AlertDialog(
            onDismissRequest = { illegalNameMessage = null },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    illegalNameMessage = null
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning_amber), null) },
            title = { Text(stringResource(Res.string.error_title)) },
            text = { Text(stringResource(id)) },
        )
    }
}

private fun LazyListScope.assetEditSettings(
    uiState: AssetEditUiState,
    viewModel: AssetEditViewModel,
) {
    item("name", PreferenceType.TEXT_FIELD) {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.route_asset_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }
    item("link", PreferenceType.TEXT_FIELD) {
        TextFieldPreference(
            value = uiState.link,
            onValueChange = { viewModel.setLink(it) },
            title = { Text(stringResource(Res.string.url)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.link), null) },
            summary = { Text(contentOrUnset(uiState.link)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                LinkOrContentTextField(value, onValueChange, onOk)
            },
        )
    }
    item("auto_update_delay", PreferenceType.TEXT_FIELD) {
        TextFieldPreference(
            value = uiState.autoUpdateDelay,
            onValueChange = { viewModel.setAutoUpdateDelay(it) },
            title = { Text(stringResource(Res.string.route_asset_auto_update_delay)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = { Icon(vectorResource(Res.drawable.timer), null) },
            summary = { Text(uiState.autoUpdateDelay.toString()) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
}
