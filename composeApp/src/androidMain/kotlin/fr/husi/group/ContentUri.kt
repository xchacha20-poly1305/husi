package fr.husi.group

import androidx.core.net.toUri
import fr.husi.repository.androidRepo

actual fun readContentUri(uri: String): String? {
    return androidRepo.context.contentResolver.openInputStream(uri.toUri())
        ?.bufferedReader()
        ?.readText()
}
