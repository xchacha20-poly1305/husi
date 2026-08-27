package fr.husi.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import fr.husi.compose.SheetActionRow
import fr.husi.compose.material3.Icon
import fr.husi.ktx.Logs
import fr.husi.ktx.createTempChild
import fr.husi.ktx.shareUri
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.send
import fr.husi.resources.share
import fr.husi.utils.SendLog
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal actual fun ShareActionRow(scope: CoroutineScope, showSnackbar: suspend (Exception) -> Unit) {
    val context = LocalContext.current
    SheetActionRow(
        text = stringResource(Res.string.share),
        leadingIcon = {
            Icon(vectorResource(Res.drawable.send), null)
        },
        onClick = {
            scope.launch {
                try {
                    shareLogFile(context)
                } catch (e: Exception) {
                    Logs.e(e)
                    showSnackbar(e)
                }
            }
        },
    )
}

private suspend fun shareLogFile(context: Context) {
    val logFile = (resolveRepository().cacheDir / "log").createTempChild(
        context.packageName,
        ".log",
    )
    logFile.writeString(SendLog.buildLog(resolveRepository().externalAssetsDir))
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(
                    Intent.EXTRA_STREAM,
                    shareUri(context, logFile),
                ),
            resolveRepository().getString(Res.string.share),
        ).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}
