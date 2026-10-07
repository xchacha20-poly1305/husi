package fr.husi.utils

import fr.husi.repository.resolveAndroidRepository
import java.io.FileNotFoundException
import java.io.InputStream

internal actual fun openBundledResource(path: String): InputStream? {
    return try {
        resolveAndroidRepository().context.assets.open(path)
    } catch (_: FileNotFoundException) {
        null
    }
}
