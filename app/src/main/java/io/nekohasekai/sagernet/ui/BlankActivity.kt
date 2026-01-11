package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.utils.SendLog
import java.io.File

class BlankActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_TITLE = "log_title"
    }

    private var sharedUri: Uri? = null
    private var sharedFile: File? = null

    private val shareLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            sharedUri?.let { uri ->
                revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            sharedFile?.delete()
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
        val logFile = File.createTempFile(
            title,
            ".log",
            File(cacheDir, "log").also { it.mkdirs() },
        ).apply {
            writeText(SendLog.buildLog(getExternalFilesDir(null) ?: filesDir))
        }
        sharedFile = logFile
        val uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".cache", logFile)
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
