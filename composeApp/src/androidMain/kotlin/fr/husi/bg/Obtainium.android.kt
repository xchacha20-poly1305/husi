package fr.husi.bg

import android.content.Intent
import androidx.core.net.toUri
import fr.husi.repository.resolveAndroidRepository

private const val OBTAINIUM_PROBE_URI = "$OBTAINIUM_SCHEME://"

fun isObtainiumInstalled(): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, OBTAINIUM_PROBE_URI.toUri())
    val packageManager = resolveAndroidRepository().packageManager
    return runCatching { packageManager.resolveActivity(intent, 0) }.getOrNull() != null
}
