@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.tailscale

import fr.husi.ktx.BrowserBytes
import fr.husi.ktx.toByteArray
import io.github.vinceglb.filekit.BrowserFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.WebFile
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.js.Promise
import kotlin.js.toJsArray

internal suspend fun PlatformFile.read(offset: Long, length: Int): ByteArray {
    val file = (webFile as WebFile.FileWrapper).file
    return readFile(file, offset.toDouble(), length).await().toByteArray()
}

internal class BrowserDownload {
    private val parts = mutableListOf<BrowserBytes>()

    fun append(bytes: BrowserBytes) {
        parts.add(bytes)
    }

    fun save(name: String) {
        val url = URL.createObjectURL(Blob(parts.toJsArray()))
        try {
            val anchor = document.createElement("a") as HTMLAnchorElement
            anchor.href = url
            anchor.download = name
            anchor.click()
        } finally {
            window.setTimeout({
                URL.revokeObjectURL(url)
                null
            }, 1000)
        }
    }
}

@JsFun("(file, offset, length) => file.slice(offset, offset + length).arrayBuffer().then(buffer => new Uint8Array(buffer))")
private external fun readFile(file: BrowserFile, offset: Double, length: Int): Promise<BrowserBytes>
