package fr.husi.core

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.Objects

internal class DesktopPipeSocket(private val path: String) : Socket() {
    private val lock = Any()
    private var pipe: Pointer? = null
    private var pending = 0

    override fun connect(endpoint: SocketAddress, timeout: Int) {
        synchronized(lock) {
            if (isClosed) {
                throw SocketException("Core pipe closed")
            }
            check(pipe == null)
            val opened = kernel.CreateFileW(
                WString(path),
                GENERIC_READ or GENERIC_WRITE,
                0,
                null,
                OPEN_EXISTING,
                FILE_FLAG_OVERLAPPED,
                null,
            )
            val failure = Native.getLastError()
            if (opened == null || Pointer.nativeValue(opened) == INVALID_HANDLE_VALUE) {
                throw pipeError("open", failure)
            }
            pipe = opened
        }
    }

    override fun getInputStream() = object : CoreSocketInputStream(this) {
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            Objects.checkFromIndexSize(offset, length, bytes.size)
            if (length == 0) return 0
            return Memory(length.toLong()).use { buffer ->
                val count = transfer(buffer, length, write = false)
                if (count > 0) {
                    buffer.read(0, bytes, offset, count)
                }
                count
            }
        }
    }

    override fun getOutputStream() = object : CoreSocketOutputStream(this) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            Objects.checkFromIndexSize(offset, length, bytes.size)
            if (length == 0) return
            Memory(length.toLong()).use { buffer ->
                buffer.write(0, bytes, offset, length)
                var written = 0
                while (written < length) {
                    val count = transfer(buffer.share(written.toLong()), length - written, write = true)
                    if (count == 0) {
                        throw IOException("Core pipe write made no progress")
                    }
                    written += count
                }
            }
        }
    }

    private fun transfer(buffer: Pointer, length: Int, write: Boolean): Int {
        val operation = if (write) "write" else "read"
        val event = kernel.CreateEventW(null, true, false, null)
            ?: throw pipeError("create event", Native.getLastError())
        try {
            val overlapped = PipeOverlapped(event)
            val count = IntByReference()
            val handle: Pointer
            val result: Boolean
            val failure: Int
            synchronized(lock) {
                val activePipe = pipe
                if (activePipe == null || isClosed) {
                    throw SocketException("Core pipe closed")
                }
                handle = activePipe
                result = if (write) {
                    kernel.WriteFile(handle, buffer, length, null, overlapped)
                } else {
                    kernel.ReadFile(handle, buffer, length, null, overlapped)
                }
                failure = if (result) {
                    0
                } else {
                    Native.getLastError()
                }
                pending++
            }
            try {
                if (!result && failure != ERROR_IO_PENDING) {
                    if (!write && failure == ERROR_BROKEN_PIPE) {
                        return -1
                    }
                    throw pipeError(operation, failure)
                }
                if (!kernel.GetOverlappedResult(handle, overlapped, count, true)) {
                    val error = Native.getLastError()
                    if (!write && error == ERROR_BROKEN_PIPE) {
                        return -1
                    }
                    throw pipeError(operation, error)
                }
                return count.value
            } finally {
                synchronized(lock) {
                    pending--
                    closeHandleIfIdle()
                }
            }
        } finally {
            kernel.CloseHandle(event)
        }
    }

    override fun isConnected(): Boolean = synchronized(lock) { pipe != null && !isClosed }

    override fun setSoTimeout(timeout: Int) {
        if (timeout != 0) {
            throw SocketException("Core pipe uses RPC deadlines")
        }
    }

    override fun getSoTimeout(): Int = 0

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            super.close()
            // Cancel the I/O before releasing its event, structure and memory in transfer().
            pipe?.let {
                kernel.CancelIoEx(it, null)
            }
            closeHandleIfIdle()
        }
    }

    private fun closeHandleIfIdle() {
        if (isClosed && pending == 0) {
            pipe?.let {
                kernel.CloseHandle(it)
            }
            pipe = null
        }
    }
}

@Structure.FieldOrder("internal", "internalHigh", "offset", "offsetHigh", "event")
internal class PipeOverlapped(@JvmField var event: Pointer? = null) : Structure() {
    @JvmField var internal: Pointer? = null
    @JvmField var internalHigh: Pointer? = null
    @JvmField var offset: Int = 0
    @JvmField var offsetHigh: Int = 0
}

private interface PipeKernel : StdCallLibrary {
    fun CreateFileW(
        name: WString,
        access: Int,
        share: Int,
        security: Pointer?,
        mode: Int,
        flags: Int,
        template: Pointer?,
    ): Pointer?

    fun CreateEventW(
        security: Pointer?,
        manualReset: Boolean,
        initialState: Boolean,
        name: WString?,
    ): Pointer?

    fun ReadFile(
        file: Pointer,
        buffer: Pointer,
        length: Int,
        count: IntByReference?,
        overlapped: PipeOverlapped,
    ): Boolean

    fun WriteFile(
        file: Pointer,
        buffer: Pointer,
        length: Int,
        count: IntByReference?,
        overlapped: PipeOverlapped,
    ): Boolean

    fun GetOverlappedResult(
        file: Pointer,
        overlapped: PipeOverlapped,
        count: IntByReference,
        wait: Boolean,
    ): Boolean

    fun CancelIoEx(file: Pointer, overlapped: PipeOverlapped?): Boolean

    fun CloseHandle(handle: Pointer): Boolean
}

private val kernel: PipeKernel by lazy { Native.load("kernel32", PipeKernel::class.java) }

private fun pipeError(operation: String, error: Int): IOException =
    IOException("Core pipe $operation failed (Windows error $error)")

private const val INVALID_HANDLE_VALUE = -1L
private const val GENERIC_READ = Int.MIN_VALUE
private const val GENERIC_WRITE = 0x40000000
private const val OPEN_EXISTING = 3
private const val FILE_FLAG_OVERLAPPED = 0x40000000
private const val ERROR_BROKEN_PIPE = 109
private const val ERROR_IO_PENDING = 997
