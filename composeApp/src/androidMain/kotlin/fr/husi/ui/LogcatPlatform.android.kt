package fr.husi.ui

import android.content.Context
import android.content.Intent
import fr.husi.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import fr.husi.compose.SheetActionRow
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.send
import fr.husi.resources.share
import fr.husi.utils.LogExport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private const val LOG_CACHE_DIR_NAME = "log"

@Composable
internal actual fun ShareActionRow(
    scope: CoroutineScope,
    buildLog: suspend () -> LogExport,
    showSnackbar: suspend (Exception) -> Unit,
) {
    val context = LocalContext.current
    SheetActionRow(
        text = stringResource(Res.string.share),
        leadingIcon = {
            Icon(vectorResource(Res.drawable.send), null)
        },
        onClick = {
            scope.launch {
                try {
                    shareLogFile(context, buildLog())
                } catch (e: Exception) {
                    Logs.e(e)
                    showSnackbar(e)
                }
            }
        },
    )
}

private suspend fun shareLogFile(context: Context, export: LogExport) {
    val repository = resolveRepository()
    val logDir = repository.cacheDir.resolve(LOG_CACHE_DIR_NAME).also { it.mkdirs() }
    val logFile = logDir.resolve(export.fileName).apply {
        writeText(export.content)
    }
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(
                    Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(
                        context, context.packageName + ".cache",
                        logFile,
                    ),
                ),
            repository.getString(Res.string.share),
        ).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}
