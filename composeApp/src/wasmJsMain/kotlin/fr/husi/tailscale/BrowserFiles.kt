@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.tailscale

import fr.husi.ktx.BrowserBytes
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.js.toJsArray

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
