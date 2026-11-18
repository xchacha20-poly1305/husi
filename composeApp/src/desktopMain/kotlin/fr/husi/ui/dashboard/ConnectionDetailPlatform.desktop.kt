package fr.husi.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.openFilePath
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists

@Composable
internal actual fun rememberOpenProcessAppInfo(process: String?): (() -> Unit)? {
    val path = process?.trim().blankAsNull() ?: return null
    val file = PlatformFile(path)
    if (!file.exists()) return null
    return remember(path) {
        {
            openFilePath(path)
        }
    }
}
