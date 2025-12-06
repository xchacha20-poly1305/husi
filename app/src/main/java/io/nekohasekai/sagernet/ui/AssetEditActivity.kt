package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.LinkOrContentTextField
import io.nekohasekai.sagernet.compose.MoreOverIcon
import io.nekohasekai.sagernet.compose.PreferenceType
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.compose.withNavigation
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference

class AssetEditActivity : ComposeActivity() {

    companion object {
        const val EXTRA_ASSET_NAME = "name"

        const val RESULT_SHOULD_UPDATE = 1
        const val RESULT_DELETE = 2
        const val RESULT_CREATED = 3
    }

    private val viewModel: AssetEditActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val editingAssetName = intent.getStringExtra(EXTRA_ASSET_NAME) ?: ""
        viewModel.initialize(editingAssetName)

        setContent {
            AppTheme {
                val context = LocalContext.current

                val isDirty by viewModel.isDirty.collectAsState()
                var showBackAlert by remember { mutableStateOf(false) }
                BackHandler(enabled = isDirty) {
                    showBackAlert = true
                }

                val uiState by viewModel.uiState.collectAsState()

                val windowInsets = WindowInsets.safeDrawing
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
                                SimpleIconButton(
                                    imageVector = ImageVector.vectorResource(R.drawable.close),
                                    contentDescription = stringResource(R.string.close),
                                ) {
                                    onBackPressedDispatcher.onBackPressed()
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
                                                finish()
                                            } else {
                                                showDeleteConfirm = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                ImageVector.vectorResource(R.drawable.delete),
                                                null,
                                            )
                                        },
                                        label = context.getString(R.string.delete),
                                    )
                                    clickableItem(
                                        onClick = ::saveAndExit,
                                        icon = {
                                            Icon(ImageVector.vectorResource(R.drawable.done), null)
                                        },
                                        label = context.getString(R.string.apply),
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
                            assetEditSettings(uiState)
                        }
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
                    icon = { Icon(ImageVector.vectorResource(R.drawable.question_mark), null) },
                    title = { Text(stringResource(R.string.unsaved_changes_prompt)) },
                )

                if (showDeleteConfirm) AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            setResult(
                                RESULT_DELETE,
                                Intent().putExtra(EXTRA_ASSET_NAME, viewModel.editingName),
                            )
                            finish()
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(android.R.string.cancel)) {
                            finish()
                        }
                    },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.warning), null) },
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
                        icon = { Icon(ImageVector.vectorResource(R.drawable.warning_amber), null) },
                        title = { Text(stringResource(R.string.error_title)) },
                        text = { Text(stringResource(id)) },
                    )
                }
            }
        }

    }

    private fun saveAndExit() {
        viewModel.save()
        val resultCode = when {
            viewModel.isNew -> RESULT_CREATED
            viewModel.shouldUpdateFromInternet -> RESULT_SHOULD_UPDATE
            else -> RESULT_OK
        }
        setResult(
            resultCode,
            Intent().putExtra(EXTRA_ASSET_NAME, viewModel.uiState.value.name),
        )
        finish()
    }

    private fun LazyListScope.assetEditSettings(uiState: AssetEditActivityUiState) {
        item("name", PreferenceType.TEXT_FIELD_PREFERENCE) {
            TextFieldPreference(
                value = uiState.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.route_asset_name)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.emoji_symbols), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.name)) },
                valueToText = { it },
            )
        }
        item("link", PreferenceType.TEXT_FIELD_PREFERENCE) {
            TextFieldPreference(
                value = uiState.link,
                onValueChange = { viewModel.setLink(it) },
                title = { Text(stringResource(R.string.url)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.link), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.link)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    LinkOrContentTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}