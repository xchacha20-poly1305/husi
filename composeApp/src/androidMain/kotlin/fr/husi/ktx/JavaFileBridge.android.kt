package fr.husi.ktx

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import java.util.zip.ZipFile

fun PlatformFile.toJavaFile(): File = when (val wrapped = androidFile) {
    is AndroidFile.FileWrapper -> wrapped.file
    is AndroidFile.UriWrapper -> {
        error("PlatformFile is a content Uri, not a filesystem path: ${wrapped.uri}")
    }
}

fun androidNoBackupFilesDir(context: Context): PlatformFile {
    return PlatformFile(context.noBackupFilesDir)
}

fun androidExternalFilesDir(context: Context): PlatformFile? {
    return context.getExternalFilesDir(null)?.let { PlatformFile(it) }
}

fun PlatformFile.canExecute(): Boolean = toJavaFile().canExecute()

fun PlatformFile.setExecutable(
    executable: Boolean,
    ownerOnly: Boolean = true,
): Boolean {
    return toJavaFile().setExecutable(executable, ownerOnly)
}

fun shareUri(context: Context, file: PlatformFile): Uri {
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.cache",
        file.toJavaFile(),
    )
}

fun openApkZip(path: String): ZipFile = ZipFile(File(path))
