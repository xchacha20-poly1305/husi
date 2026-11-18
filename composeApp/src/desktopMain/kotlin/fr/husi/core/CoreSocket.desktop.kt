package fr.husi.core

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

internal actual fun newLocalCoreSocket(path: String): Socket {
    return if (isCorePipePath(path)) {
        DesktopPipeSocket(path)
    } else {
        DesktopUnixSocket(path)
    }
}

private class DesktopUnixSocket(private val path: String) : Socket() {
    private val channel = SocketChannel.open(StandardProtocolFamily.UNIX)

    override fun connect(endpoint: SocketAddress, timeout: Int) {
        channel.connect(UnixDomainSocketAddress.of(path))
    }

    // Read and write use SocketChannel's independent locks. Channels.newInputStream
    // and newOutputStream would serialize both directions on the same blocking lock.
    override fun getInputStream(): InputStream = object : CoreSocketInputStream(this) {
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            channel.read(ByteBuffer.wrap(bytes, offset, length))
    }

    override fun getOutputStream(): OutputStream = object : CoreSocketOutputStream(this) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            val buffer = ByteBuffer.wrap(bytes, offset, length)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
        }
    }

    override fun setSoTimeout(timeout: Int) {
        if (timeout != 0) {
            throw SocketException("Unix core socket uses RPC deadlines")
        }
    }

    override fun getSoTimeout(): Int = 0

    override fun isConnected(): Boolean = channel.isConnected

    override fun close() {
        try {
            channel.close()
        } finally {
            super.close()
        }
    }
}

internal abstract class CoreSocketInputStream(private val socket: Socket) : InputStream() {
    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, byte.size) == -1) {
            -1
        } else {
            byte[0].toInt() and 0xff
        }
    }

    override fun close() = socket.close()
}

internal abstract class CoreSocketOutputStream(private val socket: Socket) : OutputStream() {
    override fun write(value: Int) = write(byteArrayOf(value.toByte()))

    override fun close() = socket.close()
}
