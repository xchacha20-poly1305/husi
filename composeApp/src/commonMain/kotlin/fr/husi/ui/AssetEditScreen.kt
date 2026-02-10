package fr.husi.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BackHandler
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.MoreOverIcon
import fr.husi.compose.PreferenceType
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.withNavigation
import fr.husi.ktx.contentOrUnset
import fr.husi.repository.repo
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
import fr.husi.resources.route_asset_name
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.url
import fr.husi.resources.warning
import fr.husi.resources.warning_amber
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

sealed interface AssetEditResult {
    data object Saved : AssetEditResult

    data class Created(
        val assetName: String,
    ) : AssetEditResult

    data class ShouldUpdate(
        val assetName: String,
    ) : AssetEditResult

    data class Deleted(
        val assetName: String,
    ) : AssetEditResult

    data object Canceled : AssetEditResult
}

@Composable
internal fun AssetEditScreen(
    assetName: String,
    onFinished: (AssetEditResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetEditViewModel = viewModel { AssetEditViewModel() },
) {
    LaunchedEffect(viewModel, assetName) {
        viewModel.initialize(assetName)
    }

    val isDirty by viewModel.isDirty.collectAsState()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (isDirty) {
            showBackAlert = true
        } else {
            onFinished(AssetEditResult.Canceled)
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
        onFinished(result)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.assets_settings)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            onFinished(AssetEditResult.Canceled)
                        }
                    }
                },
                actions = {
                    AppBarRow(
                        overflowIndicator = ::MoreOverIcon,
                    ) {
                        clickableItem(
                            onClick = {
                                val editingAssetName = viewModel.editingName
                                if (editingAssetName.isEmpty()) {
                                    onFinished(AssetEditResult.Canceled)
                                } else {
                                    showDeleteConfirm = true
                                }
                            },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.delete),
                                    null,
                                )
                            },
                            label = runBlocking { repo.getString(Res.string.delete) },
                        )
                        clickableItem(
                            onClick = ::saveAndExit,
                            icon = {
                                Icon(vectorResource(Res.drawable.done), null)
                            },
                            label = runBlocking { repo.getString(Res.string.apply) },
                        )
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            LazyColumn(
                contentPadding = innerPadding.withNavigation(),
            ) {
                assetEditSettings(
                    uiState = uiState,
                    viewModel = viewModel,
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
                    onFinished(AssetEditResult.Canceled)
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
                    onFinished(AssetEditResult.Deleted(viewModel.editingName))
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    onFinished(AssetEditResult.Canceled)
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
}
