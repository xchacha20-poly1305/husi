@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.tailscale

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BrowserFilesTest {
    @Test
    fun `file chunks cross Wasm memory without corrupting binary data`() = runTest {
        val bytes = ByteArray(64 * 1024 + 17) { it.toByte() }
        val file = fileFromBytes(bytes.toBrowserBytes())
        assertEquals(bytes.size.toDouble(), file.size)
        assertContentEquals(bytes.copyOfRange(0, 65536), file.read(0, 65536))
        assertContentEquals(bytes.copyOfRange(65536, bytes.size), file.read(65536, 65536))
        assertContentEquals(byteArrayOf(), file.read(bytes.size.toLong(), 65536))
    }
}

@JsFun("(bytes) => new File([bytes], 'binary.dat')")
private external fun fileFromBytes(bytes: BrowserBytes): BrowserFile
