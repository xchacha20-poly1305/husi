@file:OptIn(ExperimentalWasmJsInterop::class, UnsafeWasmMemoryApi::class)

package fr.husi.tailscale

import kotlin.coroutines.resume
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.toJsArray
import kotlin.js.unsafeCast
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.WebAssembly
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob

internal external interface BrowserFile : JsAny {
    val name: String
    val size: Double
}

internal external interface BrowserBytes : JsAny {
    val length: Int
}

internal suspend fun pickFiles(): List<BrowserFile> = suspendCancellableCoroutine { continuation ->
    val picker = document.createElement("input") as HTMLInputElement
    picker.type = "file"
    picker.multiple = true
    fun clearListeners() {
        picker.onchange = null
        picker.oncancel = null
    }
    picker.onchange = {
        val files = picker.files
        val selected = (0 until (files?.length ?: 0)).mapNotNull {
            files?.item(it)?.unsafeCast<BrowserFile>()
        }
        clearListeners()
        continuation.resume(selected)
    }
    picker.oncancel = {
        clearListeners()
        continuation.resume(emptyList())
    }
    continuation.invokeOnCancellation { clearListeners() }
    picker.click()
}

internal suspend fun BrowserFile.read(offset: Long, length: Int): ByteArray {
    val bytes = readFile(this, offset.toDouble(), length).await()
    return withScopedMemoryAllocator { allocator ->
        val start = allocator.allocate(bytes.length)
        copyToMemory(wasmMemory, bytes, start.address.toInt())
        ByteArray(bytes.length) { (start + it).loadByte() }
    }
}

internal fun ByteArray.toBrowserBytes(): BrowserBytes = withScopedMemoryAllocator { allocator ->
    val start = allocator.allocate(size)
    for (index in indices) {
        (start + index).storeByte(this[index])
    }
    copyFromMemory(wasmMemory, start.address.toInt(), size)
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

@JsFun("(memory, bytes, address) => new Uint8Array(memory.buffer).set(bytes, address)")
private external fun copyToMemory(memory: WebAssembly.Memory, bytes: BrowserBytes, address: Int)

@JsFun("(memory, address, length) => new Uint8Array(memory.buffer, address, length).slice()")
private external fun copyFromMemory(memory: WebAssembly.Memory, address: Int, length: Int): BrowserBytes
