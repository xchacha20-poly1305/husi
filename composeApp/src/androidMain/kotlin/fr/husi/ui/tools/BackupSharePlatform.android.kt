package fr.husi.ui.tools

import android.content.Intent
import androidx.core.content.FileProvider
import fr.husi.ktx.runOnMainDispatcher
import fr.husi.repository.androidRepo
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.share
import java.io.File

internal actual fun shareBackupFile(file: File) {
    val context = androidRepo.context
    val fileUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.cache",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra(Intent.EXTRA_STREAM, fileUri)

    runOnMainDispatcher {
        context.startActivity(
            Intent.createChooser(
                intent,
                repo.getString(Res.string.share),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
