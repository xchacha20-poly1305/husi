package fr.husi.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import fr.husi.ktx.createTempChild
import fr.husi.ktx.deleteIfExists
import fr.husi.ktx.shareUri
import fr.husi.repository.resolveRepository
import fr.husi.utils.SendLog
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.sink
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.writeString

class BlankActivity : PrivacyModeActivity() {

    companion object {
        const val EXTRA_LOG_TITLE = "log_title"
    }

    private var sharedUri: Uri? = null
    private var sharedFile: PlatformFile? = null

    private val shareLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            sharedUri?.let { uri ->
                revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            sharedFile?.let { file ->
                runBlocking { file.deleteIfExists() }
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var handled = false

        // process crash log
        intent?.getStringExtra(EXTRA_LOG_TITLE)?.let { title ->
            handled = true
            shareLogFile(title)
        }

        // If exit instantly, the receiver cann't get the intent.
        if (!handled) finish()
    }

    private fun shareLogFile(title: String) {
        val logFile = (PlatformFile(cacheDir) / "log").createTempChild(title, ".log")
        logFile.sink().buffered().use {
            it.writeString(SendLog.buildLog(resolveRepository().externalAssetsDir))
        }
        sharedFile = logFile
        val uri = shareUri(this, logFile)
        sharedUri = uri
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newUri(contentResolver, null, uri) }
        shareLauncher.launch(
            Intent.createChooser(
                shareIntent,
                getString(androidx.appcompat.R.string.abc_shareactionprovider_share_with),
            ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

}
