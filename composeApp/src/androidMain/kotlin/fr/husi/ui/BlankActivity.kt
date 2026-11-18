package fr.husi.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import fr.husi.ktx.createPlatformTempFile
import fr.husi.ktx.deleteBlocking
import fr.husi.ktx.toJavaFile
import fr.husi.ktx.writeText
import fr.husi.utils.SendLog
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories

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
            sharedFile?.deleteBlocking()
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
        val logFile = createPlatformTempFile(
            title,
            ".log",
            PlatformFile(PlatformFile(cacheDir), "log").also { it.createDirectories() },
        ).apply {
            writeText(SendLog.buildLocalLog(PlatformFile(getExternalFilesDir(null) ?: filesDir)).content)
        }
        sharedFile = logFile
        val uri = FileProvider.getUriForFile(this, packageName + ".cache", logFile.toJavaFile())
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
