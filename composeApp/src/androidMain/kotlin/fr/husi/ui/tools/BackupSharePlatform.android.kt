package fr.husi.ui.tools

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.husi.ktx.runOnMainDispatcher
import fr.husi.ktx.shareUri
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.share
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal actual fun rememberShareBackupFile(): (PlatformFile) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { file ->
            val fileUri = shareUri(context, file)
            val intent = Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM, fileUri)

            runOnMainDispatcher {
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        resolveRepository().getString(Res.string.share),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
