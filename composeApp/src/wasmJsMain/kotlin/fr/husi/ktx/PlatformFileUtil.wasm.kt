@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.ktx

import io.github.vinceglb.filekit.BrowserFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.WebFile
import kotlinx.coroutines.await
import kotlin.js.Promise

internal suspend fun PlatformFile.read(offset: Long, length: Int): ByteArray {
    val file = (webFile as WebFile.FileWrapper).file
    return readFile(file, offset.toDouble(), length).await().toByteArray()
}

@JsFun("(file, offset, length) => file.slice(offset, offset + length).arrayBuffer().then(buffer => new Uint8Array(buffer))")
private external fun readFile(file: BrowserFile, offset: Double, length: Int): Promise<BrowserBytes>
