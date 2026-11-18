package fr.husi.ktx

import android.annotation.SuppressLint
import android.net.Uri
import androidx.core.net.toUri
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile

@SuppressLint("UsableSpace")
fun PlatformFile.usableSpace(): Long = toJavaFile().usableSpace

fun PlatformFile.toAndroidUri(): Uri = when (val file = androidFile) {
    is AndroidFile.UriWrapper -> file.uri
    is AndroidFile.FileWrapper -> file.file.toUri()
}
