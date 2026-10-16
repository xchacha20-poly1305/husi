package fr.husi.tailscale

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.bg.SagerConnection
import fr.husi.compose.theme.AppTheme
import fr.husi.core.remote.RemoteControlManager
import fr.husi.ktx.Logs
import fr.husi.resources.Res
import fr.husi.resources.taildrop_no_files
import fr.husi.resources.taildrop_no_share_endpoint
import fr.husi.resources.taildrop_no_targets
import fr.husi.resources.taildrop_service_not_running
import fr.husi.ui.ComposeActivity
import fr.husi.ui.tailscale.TaildropTargetPicker
import okio.source
import org.koin.core.context.GlobalContext

class TaildropShareActivity : ComposeActivity() {

    private val serviceConnection = SagerConnection()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceConnection.connect(applicationContext)

        val shared = sharedItems(intent)
        val remoteControl: RemoteControlManager = GlobalContext.get().get()
        val statusRepository: TailscaleStatusRepository = GlobalContext.get().get()
        val sendManager: TaildropSendManager = GlobalContext.get().get()

        setContent {
            val runningTarget by remoteControl.runningTarget.collectAsStateWithLifecycle()
            val snapshot by statusRepository.snapshot.collectAsStateWithLifecycle()
            val endpoints = snapshot.endpoints
            val sessions by sendManager.sessions.collectAsStateWithLifecycle()
            val emptyMessage = when {
                shared.isEmpty() -> Res.string.taildrop_no_files
                runningTarget == null -> Res.string.taildrop_service_not_running
                endpoints.none { endpoint -> endpoint.canShareFiles } -> Res.string.taildrop_no_share_endpoint
                else -> Res.string.taildrop_no_targets
            }
            AppTheme {
                TaildropTargetPicker(
                    visible = true,
                    endpoints = if (shared.isEmpty()) emptyList() else endpoints,
                    sessions = sessions,
                    onDismiss = ::finish,
                    onSelect = { endpoint, peer ->
                        val sources = shared.mapNotNull { item -> item.open(contentResolver) }
                        sendManager.send(endpoint.endpointTag, peer.stableId, peer.displayName, sources)
                    },
                    onCancelSession = sendManager::cancel,
                    onDismissSession = sendManager::dismiss,
                    emptyMessage = emptyMessage,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection.disconnect(applicationContext)
    }
}

private sealed interface SharedItem {

    private companion object {
        const val SHARED_TEXT_FALLBACK_NAME = "text"
        const val SHARED_TEXT_NAME_MAX_LENGTH = 64
    }

    /** Returns null when the item can no longer be read. */
    fun open(contentResolver: ContentResolver): TaildropSource?

    class Content(private val uri: Uri) : SharedItem {
        override fun open(contentResolver: ContentResolver): TaildropSource? {
            val descriptor = try {
                contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                Logs.w("open shared $uri", e)
                null
            } ?: return null
            var name: String? = null
            var size: Long? = null
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
            val fileName = name?.let(::sanitizeFileName)
                ?: uri.lastPathSegment?.let(::sanitizeFileName)
                ?: SHARED_TEXT_FALLBACK_NAME
            val statSize = descriptor.statSize
            val fileSize = size ?: if (statSize >= 0) {
                statSize
            } else {
                TAILDROP_UNKNOWN_SIZE
            }
            return TaildropSource(fileName, fileSize) {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).source()
            }
        }
    }

    class Text(private val subject: String?, private val text: String) : SharedItem {
        override fun open(contentResolver: ContentResolver): TaildropSource {
            val baseName = sequenceOf(subject, text.lineSequence().firstOrNull())
                .firstNotNullOfOrNull { candidate ->
                    candidate?.take(SHARED_TEXT_NAME_MAX_LENGTH)?.let(::sanitizeFileName)
                } ?: SHARED_TEXT_FALLBACK_NAME
            return TaildropSource("$baseName.txt", text)
        }
    }
}

private fun sharedItems(intent: Intent): List<SharedItem> {
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
        )

        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
            intent,
            Intent.EXTRA_STREAM,
            Uri::class.java,
        ).orEmpty()

        else -> emptyList()
    }
    if (uris.isNotEmpty()) return uris.map { uri -> SharedItem.Content(uri) }
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    if (text.isNullOrEmpty()) return emptyList()
    return listOf(SharedItem.Text(intent.getStringExtra(Intent.EXTRA_SUBJECT), text))
}

private val FILE_NAME_FORBIDDEN_CHARACTERS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

private fun sanitizeFileName(name: String): String? {
    val cleaned = name.replace(FILE_NAME_FORBIDDEN_CHARACTERS, "_").trim()
    return cleaned.takeIf(::isTaildropPathSegment)
}
