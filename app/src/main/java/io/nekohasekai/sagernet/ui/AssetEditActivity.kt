package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.LinkOrContentTextField
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference

class AssetEditActivity : ComposeActivity() {

    companion object {
        const val EXTRA_ASSET_NAME = "name"

        const val RESULT_SHOULD_UPDATE = 1
        const val RESULT_DELETE = 2
    }

    private val viewModel: AssetEditActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val editingAssetName = intent.getStringExtra(EXTRA_ASSET_NAME) ?: ""
        viewModel.initialize(editingAssetName)

        setContent {
            AppTheme {
                val isDirty by viewModel.isDirty.collectAsState()
                var showBackAlert by remember { mutableStateOf(false) }
                BackHandler(enabled = isDirty) {
                    showBackAlert = true
                }

                val windowInsets = WindowInsets.safeDrawing
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                val scrollState = rememberScrollState()

                var showDeleteConfirm by remember { mutableStateOf(false) }
                var illegalNameMessage by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.assets_settings)) },
                            navigationIcon = {
                                SimpleIconButton(Icons.Filled.Close) {
                                    onBackPressedDispatcher.onBackPressed()
                                }
                            },
                            actions = {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                ) {
                                    val editingAssetName = viewModel.editingName
                                    if (editingAssetName.isEmpty()) {
                                        finish()
                                    } else {
                                        showDeleteConfirm = true
                                    }
                                }
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = stringResource(R.string.apply),
                                    onClick = ::saveAndExit,
                                )
                            },
                            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .verticalScroll(scrollState)
                    ) {
                        AssetEditSettings()
                    }
                }

                if (showBackAlert) AlertDialog(
                    onDismissRequest = { showBackAlert = false },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            viewModel.validate(viewModel.uiState.value.name)?.let {
                                illegalNameMessage = it
                                showBackAlert = false
                                return@TextButton
                            }
                            saveAndExit()
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(R.string.no)) {
                            showBackAlert = false
                        }
                    },
                    icon = { Icon(Icons.Filled.QuestionMark, null) },
                    title = { Text(stringResource(R.string.unsaved_changes_prompt)) },
                )

                if (showDeleteConfirm) AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            setResult(RESULT_DELETE)
                            finish()
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(android.R.string.cancel)) {
                            showDeleteConfirm = false
                        }
                    },
                    icon = { Icon(Icons.Filled.Warning, null) },
                    title = { Text(stringResource(R.string.delete_confirm_prompt)) },
                )

                illegalNameMessage.takeIf { it > 0 }?.let { id ->
                    AlertDialog(
                        onDismissRequest = { illegalNameMessage = 0 },
                        confirmButton = {
                            TextButton(stringResource(android.R.string.ok)) {
                                illegalNameMessage = 0
                            }
                        },
                        icon = { Icon(Icons.Filled.WarningAmber, null) },
                        title = { Text(stringResource(R.string.error_title)) },
                        text = { Text(stringResource(id)) },
                    )
                }
            }
        }

    }

    private fun saveAndExit() {
        viewModel.save()
        setResult(
            if (viewModel.shouldUpdateFromInternet) {
                RESULT_SHOULD_UPDATE
            } else {
                RESULT_OK
            },
            Intent().putExtra(EXTRA_ASSET_NAME, viewModel.editingName),
        )
        finish()
    }

    @Composable
    private fun AssetEditSettings() {
        ProvidePreferenceLocals {
            val uiState by viewModel.uiState.collectAsState()
            TextFieldPreference(
                value = uiState.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.route_asset_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.name)) },
                valueToText = { it },
            )
            TextFieldPreference(
                value = uiState.link,
                onValueChange = { viewModel.setLink(it) },
                title = { Text(stringResource(R.string.url)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Link, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.link)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    LinkOrContentTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}