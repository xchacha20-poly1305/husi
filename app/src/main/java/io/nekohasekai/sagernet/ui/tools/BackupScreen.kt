@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.ui.tools

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import java.io.File

@Composable
internal fun BackupScreen(
    modifier: Modifier = Modifier,
    onVisibleChange: (Boolean) -> Unit,
    viewModel: BackupViewModel = viewModel(),
    showSnackbar: (message: String) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val visible by rememberScrollHideState(scrollState)

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var errorDialog by remember { mutableStateOf<String?>(null) }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)
            ?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    .let(cursor::getString)
            }
        if (fileName == null || !fileName.endsWith(".json", ignoreCase = true)) {
            showSnackbar("Selected file is not a .json backup file.")
            return@rememberLauncherForActivityResult
        }
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            errorDialog = "Failed to open file"
            return@rememberLauncherForActivityResult
        }
        viewModel.inputFromStream(
            inputStream = inputStream,
            onError = showSnackbar,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
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
                    text = stringResource(R.string.action_export),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                CheckBoxLine(
                    checked = uiState.backupGroupsAndConfig,
                    onCheckedChange = { viewModel.setBackupGroupsAndConfig(it) },
                    text = stringResource(R.string.backup_groups_and_configurations),
                )
                CheckBoxLine(
                    checked = uiState.backupRules,
                    onCheckedChange = { viewModel.setBackupRules(it) },
                    text = stringResource(R.string.backup_rules),
                )
                CheckBoxLine(
                    checked = uiState.backupSettings,
                    onCheckedChange = { viewModel.setBackupSettings(it) },
                    text = stringResource(R.string.backup_settings),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.export() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_export))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.share(
                            createFile = { fileName ->
                                File(context.cacheDir, fileName)
                            },
                            launch = { file ->
                                val fileUri = FileProvider.getUriForFile(
                                    context,
                                    BuildConfig.APPLICATION_ID + ".cache",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND)
                                    .setType("application/json")
                                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(Intent.EXTRA_STREAM, fileUri)
                                Intent.createChooser(
                                    intent,
                                    context.getString(androidx.appcompat.R.string.abc_shareactionprovider_share_with),
                                )
                            },
                            onFailed = { message ->
                                showSnackbar(message)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.share))
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
                    text = stringResource(R.string.action_import_file),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.backup_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        importFileLauncher.launch("/")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_import_file))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    errorDialog?.let { error ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            confirmButton = {
                TextButton(stringResource(android.R.string.ok)) {
                    errorDialog = null
                }
            },
            icon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.error),
                    null,
                )
            },
            title = { Text(stringResource(R.string.error_title)) },
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
                TextButton(stringResource(R.string.backup_import)) {
                    viewModel.finishInput(
                        inputResult,
                        importGroupsAndConfig,
                        importRules,
                        importSettings,
                    )
                }
            },
            dismissButton = {
                TextButton(stringResource(android.R.string.cancel)) {
                    viewModel.clearInputResult()
                }
            },
            icon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.question_mark),
                    null,
                )
            },
            title = { Text(stringResource(R.string.backup_import)) },
            text = {
                CheckBoxLine(
                    checked = importGroupsAndConfig,
                    onCheckedChange = { importGroupsAndConfig = it },
                    text = stringResource(R.string.backup_groups_and_configurations),
                )
                CheckBoxLine(
                    checked = importRules,
                    onCheckedChange = { importRules = it },
                    text = stringResource(R.string.backup_rules),
                )
                CheckBoxLine(
                    checked = importSettings,
                    onCheckedChange = { importSettings = it },
                    text = stringResource(R.string.backup_settings),
                )
                Text(
                    text = stringResource(R.string.backup_import_summary),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                )
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