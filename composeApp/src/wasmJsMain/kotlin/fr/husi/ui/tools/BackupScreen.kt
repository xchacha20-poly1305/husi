package fr.husi.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Checkbox
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.theme.HusiScrollbarDefaults
import fr.husi.compose.withNavigation
import fr.husi.ktx.Logs
import fr.husi.remote.ClashSession
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.action_export
import fr.husi.resources.action_export_msg
import fr.husi.resources.action_import_file
import fr.husi.resources.action_import_msg
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.backup
import fr.husi.resources.backup_import
import fr.husi.resources.backup_import_summary
import fr.husi.resources.cancel
import fr.husi.resources.delete
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.ok
import fr.husi.resources.question_mark
import fr.husi.resources.settings
import fr.husi.resources.web_backup_import_summary
import fr.husi.resources.web_backup_invalid_file
import fr.husi.resources.web_backup_servers
import fr.husi.resources.web_clash_storage
import fr.husi.resources.web_clash_storage_delete
import fr.husi.resources.web_clash_storage_delete_confirm
import fr.husi.resources.web_clash_storage_delete_success
import fr.husi.resources.web_clash_storage_empty
import fr.husi.resources.web_clash_storage_key
import fr.husi.resources.web_clash_storage_key_invalid
import fr.husi.resources.web_clash_storage_pull
import fr.husi.resources.web_clash_storage_push
import fr.husi.resources.web_clash_storage_push_success
import fr.husi.resources.web_clash_storage_push_summary
import fr.husi.resources.web_clash_storage_summary
import fr.husi.ui.LocalNavigator
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.download
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RemoteBackupBinding() {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<BackupViewModel>()
    BackupScreen(
        remote = remote,
        onBackPress = { navigator.popBackStack() },
        viewModel = viewModel,
    )
}

@Composable
internal fun BackupScreen(
    remote: RemoteState,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var errorDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val importFileLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf(BackupViewModel.BACKUP_FILE_EXTENSION)),
    ) { file ->
        if (file == null) return@rememberFilePickerLauncher
        if (!file.name.endsWith(".${BackupViewModel.BACKUP_FILE_EXTENSION}", ignoreCase = true)) {
            snackbar.show(StringOrRes.Res(Res.string.web_backup_invalid_file))
            return@rememberFilePickerLauncher
        }
        scope.launch {
            val bytes = try {
                file.readBytes()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.e("Reading backup file failed", failure)
                errorDialog = failure.message?.takeIf { it.isNotBlank() } ?: failure.toString()
                return@launch
            }
            viewModel.inputFromBytes(
                bytes = bytes,
                onError = snackbar::show,
            )
        }
    }

    val hazeState = rememberHazeState()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                hazeState = hazeState,
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                title = { Text(stringResource(Res.string.backup)) },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val contentPadding = innerPadding.withNavigation()
        val layoutDirection = LocalLayoutDirection.current
        Row(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    )
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
                Spacer(modifier = Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.action_export),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CheckBoxLine(
                            checked = uiState.backupServers,
                            onCheckedChange = viewModel::setBackupServers,
                            text = stringResource(Res.string.web_backup_servers),
                        )
                        CheckBoxLine(
                            checked = uiState.backupSettings,
                            onCheckedChange = viewModel::setBackupSettings,
                            text = stringResource(Res.string.settings),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.export(
                                    download = { bytes, name -> FileKit.download(bytes, name) },
                                    onSuccess = {
                                        snackbar.show(StringOrRes.Res(Res.string.action_export_msg))
                                    },
                                    onFailed = snackbar::show,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.hasSelection && !uiState.isExporting,
                        ) {
                            Text(stringResource(Res.string.action_export))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.action_import_file),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.web_backup_import_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { importFileLauncher.launch() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isImporting,
                        ) {
                            Text(stringResource(Res.string.action_import_file))
                        }
                    }
                }

                if (remote.isClash) {
                    Spacer(modifier = Modifier.height(16.dp))

                    ClashStorageCard(
                        remote = remote,
                        uiState = uiState,
                        storageKeyState = viewModel.storageKeyState,
                        onPush = {
                            viewModel.pushToClashStorage(
                                onSuccess = {
                                    snackbar.show(StringOrRes.Res(Res.string.web_clash_storage_push_success))
                                },
                                onFailed = snackbar::show,
                            )
                        },
                        onPull = {
                            viewModel.pullFromClashStorage(
                                onNotFound = {
                                    snackbar.show(StringOrRes.Res(Res.string.web_clash_storage_empty))
                                },
                                onFailed = snackbar::show,
                            )
                        },
                        onDelete = { showDeleteConfirm = true },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
            }

            BoxedVerticalScrollbar(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = scrollState),
                style = HusiScrollbarDefaults.style(),
            )
        }
    }

    errorDialog?.let { error ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    errorDialog = null
                }
            },
            icon = {
                Icon(
                    vectorResource(Res.drawable.error),
                    null,
                )
            },
            title = { Text(stringResource(Res.string.error_title)) },
            text = { Text(error) },
        )
    }

    uiState.inputResult?.let { inputResult ->
        var importServers by remember(inputResult) {
            mutableStateOf(inputResult.servers != null)
        }
        var importSettings by remember(inputResult) {
            mutableStateOf(inputResult.settings != null)
        }
        val hasImportSelection =
            (importServers && inputResult.servers != null) ||
                (importSettings && inputResult.settings != null)
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.finishInput(
                            content = inputResult,
                            importServers = importServers,
                            importSettings = importSettings,
                            onSuccess = {
                                snackbar.show(StringOrRes.Res(Res.string.action_import_msg))
                            },
                            onFailed = snackbar::show,
                        )
                    },
                    enabled = hasImportSelection,
                ) {
                    Text(stringResource(Res.string.backup_import))
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    viewModel.clearInputResult()
                }
            },
            icon = {
                Icon(
                    vectorResource(Res.drawable.question_mark),
                    null,
                )
            },
            title = { Text(stringResource(Res.string.backup_import)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (inputResult.servers != null) {
                        CheckBoxLine(
                            checked = importServers,
                            onCheckedChange = { importServers = it },
                            text = stringResource(Res.string.web_backup_servers),
                        )
                    }
                    if (inputResult.settings != null) {
                        CheckBoxLine(
                            checked = importSettings,
                            onCheckedChange = { importSettings = it },
                            text = stringResource(Res.string.settings),
                        )
                    }
                    Text(
                        text = stringResource(Res.string.backup_import_summary),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                }
            },
        )
    }

    if (showDeleteConfirm) {
        val key = BackupViewModel.normalizeStorageKey(viewModel.storageKeyState.text.toString())
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(stringResource(Res.string.delete)) {
                    showDeleteConfirm = false
                    viewModel.deleteFromClashStorage(
                        onSuccess = {
                            snackbar.show(StringOrRes.Res(Res.string.web_clash_storage_delete_success))
                        },
                        onFailed = snackbar::show,
                    )
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showDeleteConfirm = false
                }
            },
            title = { Text(stringResource(Res.string.web_clash_storage_delete)) },
            text = {
                Text(stringResource(Res.string.web_clash_storage_delete_confirm, key))
            },
        )
    }

    if (uiState.isImporting) {
        Dialog(
            onDismissRequest = {},
        ) {
            CircularWavyProgressIndicator()
        }
    }
}

@Composable
private fun CheckBoxLine(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.graphicsLayer {
                scaleX = 1.2f
                scaleY = 1.2f
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ClashStorageCard(
    remote: RemoteState,
    uiState: BackupUiState,
    storageKeyState: TextFieldState,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onDelete: () -> Unit,
) {
    val keyValid = BackupViewModel.isValidStorageKey(storageKeyState.text.toString())
    val canUseStorage = remote.connected &&
        remote.currentSession is ClashSession &&
        keyValid &&
        !uiState.isClashStorageBusy
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.web_clash_storage),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.isClashStorageBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.web_clash_storage_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                state = storageKeyState,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isClashStorageBusy,
                label = { Text(stringResource(Res.string.web_clash_storage_key)) },
                placeholder = { Text(BackupViewModel.DEFAULT_CLASH_STORAGE_KEY) },
                isError = !keyValid,
                supportingText = if (!keyValid) {
                    { Text(stringResource(Res.string.web_clash_storage_key_invalid)) }
                } else {
                    null
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPush,
                modifier = Modifier.fillMaxWidth(),
                enabled = canUseStorage && uiState.hasSelection,
            ) {
                Text(stringResource(Res.string.web_clash_storage_push))
            }
            Text(
                text = stringResource(Res.string.web_clash_storage_push_summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onPull,
                modifier = Modifier.fillMaxWidth(),
                enabled = canUseStorage,
            ) {
                Text(stringResource(Res.string.web_clash_storage_pull))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                enabled = canUseStorage,
            ) {
                Text(stringResource(Res.string.web_clash_storage_delete))
            }
        }
    }
}
