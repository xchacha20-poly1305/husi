@file:OptIn(ExperimentalWasmJsInterop::class, UnsafeWasmMemoryApi::class)

package fr.husi.ktx

import kotlin.js.JsAny
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.WebAssembly
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

internal external interface BrowserBytes : JsAny {
    val length: Int
}

internal fun BrowserBytes.toByteArray(): ByteArray = withScopedMemoryAllocator { allocator ->
    val start = allocator.allocate(length)
    copyToMemory(wasmMemory, this, start.address.toInt())
    ByteArray(length) { (start + it).loadByte() }
}

internal fun ByteArray.toBrowserBytes(): BrowserBytes = withScopedMemoryAllocator { allocator ->
    val start = allocator.allocate(size)
    for (index in indices) {
        (start + index).storeByte(this[index])
    }
    copyFromMemory(wasmMemory, start.address.toInt(), size)
}

@JsFun("(memory, bytes, address) => new Uint8Array(memory.buffer).set(bytes, address)")
private external fun copyToMemory(memory: WebAssembly.Memory, bytes: BrowserBytes, address: Int)

@JsFun("(memory, address, length) => new Uint8Array(memory.buffer, address, length).slice()")
private external fun copyFromMemory(memory: WebAssembly.Memory, address: Int, length: Int): BrowserBytes
