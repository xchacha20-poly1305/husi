@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.TextButton
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.rememberScrollHideState
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import fr.husi.resources.*
import fr.husi.repository.repo
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter

@Composable
internal fun BackupScreen(
    modifier: Modifier = Modifier,
    onVisibleChange: (Boolean) -> Unit,
    viewModel: BackupViewModel = viewModel { BackupViewModel() },
    showSnackbar: (message: String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val visible by rememberScrollHideState(scrollState)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var errorDialog by remember { mutableStateOf<String?>(null) }

    val exportFileLauncher = rememberFileSaverLauncher { file ->
        if (file == null) {
            viewModel.postExport()
            return@rememberFileSaverLauncher
        }
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                file.write(uiState.exported!!.encodeToByteArray())
                showSnackbar(repo.getString(Res.string.action_export_msg))
            } catch (e: Exception) {
                Logs.e(e)
                showSnackbar(e.readableMessage)
            } finally {
                viewModel.postExport()
            }
        }
    }
    LaunchedEffect(uiState.exported) {
        uiState.exported?.let {
            val time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val fileName = "husi_backup_${time}"
            exportFileLauncher.launch(fileName, "json")
        }
    }

    val importFileLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("json")),
    ) { file ->
        if (file == null) return@rememberFilePickerLauncher
        val fileName = file.name
        if (!fileName.endsWith(".json", ignoreCase = true)) {
            showSnackbar("Selected file is not a .json backup file.")
            return@rememberFilePickerLauncher
        }
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

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
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
                        text = stringResource(Res.string.backup_settings),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.export() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.action_export))
                    }
                    if (repo.isAndroid) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.share(
                                    createFile = { fileName ->
                                        File(repo.cacheDir, fileName)
                                    },
                                    launch = { file ->
                                        shareBackupFile(file)
                                    },
                                    onFailed = { message ->
                                        showSnackbar(message)
                                    },
                                )
                            },
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
        }

        BoxedVerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = scrollState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
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
                        text = stringResource(Res.string.backup_settings),
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

    if (uiState.isImporting) Dialog(
        onDismissRequest = {},
    ) {
        CircularWavyProgressIndicator()
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
