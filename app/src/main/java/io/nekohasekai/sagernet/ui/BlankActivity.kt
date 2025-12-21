package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.utils.SendLog
import java.io.File

class BlankActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_TITLE = "log_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // process crash log
        intent?.getStringExtra(EXTRA_LOG_TITLE)?.let { title ->
            val logFile = File.createTempFile(
                title,
                ".log",
                File(cacheDir, "log").also { it.mkdirs() },
            ).apply {
                writeText(SendLog.buildLog(getExternalFilesDir(null) ?: filesDir))
            }
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/x-log")
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(
                            Intent.EXTRA_STREAM,
                            FileProvider.getUriForFile(
                                this, BuildConfig.APPLICATION_ID + ".cache", logFile,
                            ),
                        ),
                    getString(androidx.appcompat.R.string.abc_shareactionprovider_share_with),
                ).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }

        finish()
    }

}