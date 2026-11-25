package fr.husi.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Checkbox
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.RadioButton
import fr.husi.compose.material3.Text
import fr.husi.compose.theme.HusiScrollbarDefaults
import fr.husi.compose.withNavigation
import fr.husi.ktx.DisplayTime
import fr.husi.ktx.Logs
import fr.husi.ktx.formatBytes
import fr.husi.ktx.readableMessage
import fr.husi.net.WebDAVEntry
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_export
import fr.husi.resources.action_export_msg
import fr.husi.resources.action_import_file
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.backup
import fr.husi.resources.backup_age_armor
import fr.husi.resources.backup_age_decrypt_failed
import fr.husi.resources.backup_age_identity_invalid
import fr.husi.resources.backup_age_no_matching_identity
import fr.husi.resources.backup_age_private_key
import fr.husi.resources.backup_age_recipient
import fr.husi.resources.backup_age_recipient_invalid
import fr.husi.resources.backup_age_recipient_optional
import fr.husi.resources.backup_age_restore_title
import fr.husi.resources.backup_groups_and_configurations
import fr.husi.resources.backup_import
import fr.husi.resources.backup_import_summary
import fr.husi.resources.backup_rules
import fr.husi.resources.backup_summary
import fr.husi.resources.cancel
import fr.husi.resources.delete
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.hide
import fr.husi.resources.ok
import fr.husi.resources.password
import fr.husi.resources.question_mark
import fr.husi.resources.settings
import fr.husi.resources.share
import fr.husi.resources.show
import fr.husi.resources.username
import fr.husi.resources.visibility
import fr.husi.resources.visibility_off
import fr.husi.resources.webdav
import fr.husi.resources.webdav_configure
import fr.husi.resources.webdav_delete
import fr.husi.resources.webdav_delete_confirm
import fr.husi.resources.webdav_delete_success
import fr.husi.resources.webdav_directory_url
import fr.husi.resources.webdav_no_backups
import fr.husi.resources.webdav_not_configured
import fr.husi.resources.webdav_restore
import fr.husi.resources.webdav_upload
import fr.husi.resources.webdav_upload_success
import fr.husi.resources.webdav_upload_summary
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import io.github.xchacha20_poly1305.yan.Age
import io.github.xchacha20_poly1305.yan.KeyFormatException
import io.github.xchacha20_poly1305.yan.NoMatchingIdentityException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.time.Instant

private enum class BackupDestination(val title: StringResource) {
    EXPORT(Res.string.action_export),
    SHARE(Res.string.share),
    WEBDAV(Res.string.webdav_upload),
}

@Composable
internal fun BackupScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    viewModel: BackupViewModel = viewModel { BackupViewModel() },
) {
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val shareBackupFile = rememberShareBackupFile()
    val snackbar = LocalSnackbarEmitter.current
    val showSnackbar: (String) -> Unit = { message ->
        snackbar.show(StringOrRes.Direct(message))
    }
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var errorDialog by remember { mutableStateOf<String?>(null) }
    var showWebDAVConfig by rememberSaveable { mutableStateOf(false) }
    var backupDestination by remember { mutableStateOf<BackupDestination?>(null) }

    val exportFileLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
        onError = { error ->
            Logs.e(error)
            showSnackbar(error.readableMessage)
            viewModel.postExport()
        },
    ) { file ->
        val backup = viewModel.uiState.value.exported ?: return@rememberFileSaverLauncher
        if (file == null) {
            viewModel.postExport()
            return@rememberFileSaverLauncher
        }
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                file.write(backup.bytes)
                showSnackbar(resolveRepository().getString(Res.string.action_export_msg))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.e(e)
                showSnackbar(e.readableMessage)
            } finally {
                viewModel.postExport()
            }
        }
    }
    LaunchedEffect(uiState.exported) {
        uiState.exported?.let { backup ->
            exportFileLauncher.launch(
                suggestedName = if (backup.encrypted) backup.name else backup.name.substringBeforeLast('.'),
                defaultExtension = if (backup.encrypted) null else BackupViewModel.BACKUP_FILE_EXTENSION,
            )
        }
    }

    val importFileLauncher = rememberFilePickerLauncher { file ->
        if (file == null) return@rememberFilePickerLauncher
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                Logs.e(e)
                errorDialog = e.readableMessage
                return@launch
            }
            viewModel.inputFromBytes(
                bytes = bytes,
                onError = showSnackbar,
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
                            checked = uiState.backupGroupsAndConfig,
                            onCheckedChange = { viewModel.setBackupGroupsAndConfig(it) },
                            text = stringResource(Res.string.backup_groups_and_configurations),
                        )
                        CheckBoxLine(
                            checked = uiState.backupRules,
                            onCheckedChange = { viewModel.setBackupRules(it) },
                            text = stringResource(Res.string.backup_rules),
                        )
                        CheckBoxLine(
                            checked = uiState.backupSettings,
                            onCheckedChange = { viewModel.setBackupSettings(it) },
                            text = stringResource(Res.string.settings),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { backupDestination = BackupDestination.EXPORT },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isExporting && uiState.exported == null,
                        ) {
                            Text(stringResource(Res.string.action_export))
                        }
                        if (PlatformInfo.isAndroid) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { backupDestination = BackupDestination.SHARE },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(Res.string.share))
                            }
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
                            text = stringResource(Res.string.backup_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                importFileLauncher.launch()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.action_import_file))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                WebDAVCard(
                    uiState = uiState,
                    onConfigure = { showWebDAVConfig = true },
                    onUpload = { backupDestination = BackupDestination.WEBDAV },
                    onRestore = {
                        viewModel.loadWebDAVBackups(WebDAVBackupsPurpose.RESTORE, onFailed = showSnackbar)
                    },
                    onDelete = {
                        viewModel.loadWebDAVBackups(WebDAVBackupsPurpose.DELETE, onFailed = showSnackbar)
                    },
                )

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

    backupDestination?.let { destination ->
        BackupEncryptionDialog(
            title = destination.title,
            onConfirm = { encryption ->
                backupDestination = null
                when (destination) {
                    BackupDestination.EXPORT -> viewModel.export(encryption, onFailed = showSnackbar)
                    BackupDestination.SHARE -> viewModel.share(encryption, shareBackupFile, onFailed = showSnackbar)
                    BackupDestination.WEBDAV -> viewModel.uploadToWebDAV(
                        encryption = encryption,
                        onSuccess = {
                            snackbar.show(StringOrRes.Res(Res.string.webdav_upload_success))
                        },
                        onFailed = showSnackbar,
                    )
                }
            },
            onDismiss = { backupDestination = null },
        )
    }

    uiState.encryptedInput?.let { backup ->
        key(backup) {
            BackupDecryptionDialog(
                onDecrypt = { identity -> viewModel.decryptInput(backup, identity) },
                onDismiss = viewModel::dismissEncryptedInput,
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
        var importGroupsAndConfig by rememberSaveable { mutableStateOf(true) }
        var importRules by rememberSaveable { mutableStateOf(true) }
        var importSettings by rememberSaveable { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(stringResource(Res.string.backup_import)) {
                    viewModel.finishInput(
                        inputResult,
                        importGroupsAndConfig,
                        importRules,
                        importSettings,
                    )
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
                    CheckBoxLine(
                        checked = importGroupsAndConfig,
                        onCheckedChange = { importGroupsAndConfig = it },
                        text = stringResource(Res.string.backup_groups_and_configurations),
                    )
                    CheckBoxLine(
                        checked = importRules,
                        onCheckedChange = { importRules = it },
                        text = stringResource(Res.string.backup_rules),
                    )
                    CheckBoxLine(
                        checked = importSettings,
                        onCheckedChange = { importSettings = it },
                        text = stringResource(Res.string.settings),
                    )
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

    if (showWebDAVConfig) {
        WebDAVConfigDialog(
            initial = uiState.webdav,
            onConfirm = { config ->
                viewModel.saveWebDAVConfig(config)
                showWebDAVConfig = false
            },
            onDismiss = { showWebDAVConfig = false },
        )
    }

    uiState.webdavBackups?.let { webdavBackups ->
        when (webdavBackups.purpose) {
            WebDAVBackupsPurpose.RESTORE -> WebDAVRestoreDialog(
                backups = webdavBackups.entries,
                onRestore = { entry ->
                    viewModel.restoreFromWebDAV(entry, onFailed = showSnackbar)
                },
                onDismiss = viewModel::dismissWebDAVBackups,
            )

            WebDAVBackupsPurpose.DELETE -> WebDAVDeleteDialog(
                backups = webdavBackups.entries,
                onDelete = { entries ->
                    viewModel.deleteFromWebDAV(
                        entries = entries,
                        onSuccess = {
                            snackbar.show(StringOrRes.Res(Res.string.webdav_delete_success))
                        },
                        onFailed = showSnackbar,
                    )
                },
                onDismiss = viewModel::dismissWebDAVBackups,
            )
        }
    }

    if (uiState.isImporting) Dialog(
        onDismissRequest = {},
    ) {
        CircularWavyProgressIndicator()
    }
}

@Composable
private fun BackupEncryptionDialog(
    title: StringResource,
    onConfirm: (BackupEncryption?) -> Unit,
    onDismiss: () -> Unit,
) {
    var recipient by remember { mutableStateOf("") }
    var armor by remember { mutableStateOf(false) }
    var invalidRecipient by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                val encryption = try {
                    if (recipient.isBlank()) null else BackupEncryption(Age.parseRecipient(recipient.trim()), armor)
                } catch (_: KeyFormatException) {
                    invalidRecipient = true
                    return@TextButton
                }
                onConfirm(encryption)
            }
        },
        dismissButton = { TextButton(stringResource(Res.string.cancel), onDismiss) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = {
                        recipient = it
                        invalidRecipient = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.backup_age_recipient)) },
                    supportingText = {
                        val message = if (invalidRecipient) {
                            Res.string.backup_age_recipient_invalid
                        } else {
                            Res.string.backup_age_recipient_optional
                        }
                        Text(stringResource(message))
                    },
                    isError = invalidRecipient,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                val armorEnabled = recipient.isNotBlank()
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = armor && armorEnabled,
                        enabled = armorEnabled,
                        role = Role.Checkbox,
                        onValueChange = { armor = it },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = armor && armorEnabled, onCheckedChange = null, enabled = armorEnabled)
                    Text(stringResource(Res.string.backup_age_armor))
                }
            }
        },
    )
}

@Composable
private fun BackupDecryptionDialog(
    onDecrypt: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var identity by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<StringResource?>(null) }
    var decrypting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!decrypting) onDismiss() },
        title = { Text(stringResource(Res.string.backup_age_restore_title)) },
        confirmButton = {
            TextButton(
                enabled = identity.isNotBlank() && !decrypting,
                onClick = {
                    decrypting = true
                    error = null
                    scope.launch {
                        try {
                            onDecrypt(identity)
                            identity = ""
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: KeyFormatException) {
                            error = Res.string.backup_age_identity_invalid
                        } catch (_: NoMatchingIdentityException) {
                            error = Res.string.backup_age_no_matching_identity
                        } catch (_: Exception) {
                            error = Res.string.backup_age_decrypt_failed
                        } finally {
                            decrypting = false
                        }
                    }
                },
            ) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !decrypting) {
                Text(stringResource(Res.string.cancel))
            }
        },
        text = {
            BackupIdentityField(
                identity = identity,
                onIdentityChange = {
                    identity = it
                    error = null
                },
                error = error,
                enabled = !decrypting,
            )
        },
    )
}

@Composable
private fun BackupIdentityField(
    identity: String,
    onIdentityChange: (String) -> Unit,
    error: StringResource? = null,
    enabled: Boolean = true,
) {
    var identityVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = identity,
        onValueChange = onIdentityChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.backup_age_private_key)) },
        supportingText = error?.let { { Text(stringResource(it)) } },
        isError = error != null,
        enabled = enabled,
        maxLines = 4,
        visualTransformation = if (identityVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            SimpleIconButton(
                imageVector = vectorResource(if (identityVisible) Res.drawable.visibility_off else Res.drawable.visibility),
                contentDescription = stringResource(if (identityVisible) Res.string.hide else Res.string.show),
                enabled = enabled,
                onClick = { identityVisible = !identityVisible },
            )
        },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
    )
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
            .clickable { onCheckedChange(!checked) }
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
private fun WebDAVCard(
    uiState: BackupUiState,
    onConfigure: () -> Unit,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val config = uiState.webdav
    val actionsEnabled = config.isConfigured && !uiState.isWebDAVBusy
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
                    text = stringResource(Res.string.webdav),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.isWebDAVBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (config.isConfigured) {
                    config.url
                } else {
                    stringResource(Res.string.webdav_not_configured)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isWebDAVBusy,
            ) {
                Text(stringResource(Res.string.webdav_configure))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onUpload,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            ) {
                Text(stringResource(Res.string.webdav_upload))
            }
            Text(
                text = stringResource(Res.string.webdav_upload_summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            ) {
                Text(stringResource(Res.string.webdav_restore))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            ) {
                Text(stringResource(Res.string.webdav_delete))
            }
        }
    }
}

@Composable
private fun WebDAVConfigDialog(
    initial: WebDAVConfig,
    onConfirm: (WebDAVConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initial.url) }
    var username by rememberSaveable { mutableStateOf(initial.username) }
    var password by rememberSaveable { mutableStateOf(initial.password) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                onConfirm(WebDAVConfig(url = url, username = username, password = password))
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel), onDismiss)
        },
        title = { Text(stringResource(Res.string.webdav)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.webdav_directory_url)) },
                    placeholder = { Text("https://dav.example.com/husi/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun WebDAVRestoreDialog(
    backups: List<WebDAVEntry>,
    onRestore: (WebDAVEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onRestore(backups.first { it.name == selectedName }) },
                enabled = selectedName != null,
            ) {
                Text(stringResource(Res.string.backup_import))
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel), onDismiss)
        },
        title = { Text(stringResource(Res.string.webdav_restore)) },
        text = {
            WebDAVBackupList(backups) { entry ->
                val selected = entry.name == selectedName
                WebDAVBackupRow(
                    entry = entry,
                    modifier = Modifier.selectable(
                        selected = selected,
                        onClick = { selectedName = entry.name },
                        role = Role.RadioButton,
                    ),
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                    )
                }
            }
        },
    )
}

@Composable
private fun WebDAVDeleteDialog(
    backups: List<WebDAVEntry>,
    onDelete: (List<WebDAVEntry>) -> Unit,
    onDismiss: () -> Unit,
) {
    var checkedNames by rememberSaveable { mutableStateOf(setOf<String>()) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { confirming = true },
                enabled = checkedNames.isNotEmpty(),
            ) {
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel), onDismiss)
        },
        title = { Text(stringResource(Res.string.webdav_delete)) },
        text = {
            WebDAVBackupList(backups) { entry ->
                val checked = entry.name in checkedNames
                WebDAVBackupRow(
                    entry = entry,
                    modifier = Modifier.toggleable(
                        value = checked,
                        onValueChange = { check ->
                            checkedNames = if (check) checkedNames + entry.name else checkedNames - entry.name
                        },
                        role = Role.Checkbox,
                    ),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                    )
                }
            }
        },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            confirmButton = {
                TextButton(stringResource(Res.string.delete)) {
                    onDelete(backups.filter { it.name in checkedNames })
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    confirming = false
                }
            },
            title = { Text(stringResource(Res.string.webdav_delete)) },
            text = {
                Text(pluralStringResource(Res.plurals.webdav_delete_confirm, checkedNames.size, checkedNames.size))
            },
        )
    }
}

@Composable
private fun WebDAVBackupList(
    backups: List<WebDAVEntry>,
    row: @Composable (WebDAVEntry) -> Unit,
) {
    if (backups.isEmpty()) {
        Text(stringResource(Res.string.webdav_no_backups))
    } else {
        LazyColumn {
            items(backups, key = { it.name }) { entry ->
                row(entry)
            }
        }
    }
}

@Composable
private fun WebDAVBackupRow(
    entry: WebDAVEntry,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            val details = buildList {
                if (entry.modified > 0) {
                    add(DisplayTime.dateTime(Instant.fromEpochMilliseconds(entry.modified)))
                }
                add(entry.size.formatBytes())
            }
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
