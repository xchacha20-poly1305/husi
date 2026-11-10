@file:OptIn(ExperimentalWasmJsInterop::class, UnsafeWasmMemoryApi::class)

package fr.husi.tailscale

import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.Promise
import kotlin.js.toList
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.WebAssembly
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.coroutines.await

internal external interface BrowserFile : JsAny {
    val name: String
    val size: Double
}

internal external interface BrowserBytes : JsAny {
    val length: Int
}

internal suspend fun pickFiles(): List<BrowserFile> = selectFiles().await().toList()

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

internal external interface BrowserDownload : JsAny {
    fun append(bytes: BrowserBytes)
    fun save(name: String)
}

@JsFun("""() => new Promise(resolve => {
    const picker = document.createElement('input');
    picker.type = 'file';
    picker.multiple = true;
    picker.onchange = () => resolve(Array.from(picker.files || []));
    picker.oncancel = () => resolve([]);
    picker.click();
})""")
private external fun selectFiles(): Promise<JsArray<BrowserFile>>

@JsFun("(file, offset, length) => file.slice(offset, offset + length).arrayBuffer().then(buffer => new Uint8Array(buffer))")
private external fun readFile(file: BrowserFile, offset: Double, length: Int): Promise<BrowserBytes>

@JsFun("(memory, bytes, address) => new Uint8Array(memory.buffer).set(bytes, address)")
private external fun copyToMemory(memory: WebAssembly.Memory, bytes: BrowserBytes, address: Int)

@JsFun("(memory, address, length) => new Uint8Array(memory.buffer, address, length).slice()")
private external fun copyFromMemory(memory: WebAssembly.Memory, address: Int, length: Int): BrowserBytes

@JsFun("""() => {
    const parts = [];
    return {
        append(bytes) { parts.push(bytes); },
        save(name) {
            const url = URL.createObjectURL(new Blob(parts));
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = name;
            anchor.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        }
    };
}""")
internal external fun browserDownload(): BrowserDownload
