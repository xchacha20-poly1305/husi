package fr.husi.core

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.net.Socket
import java.net.SocketAddress

internal actual fun newLocalCoreSocket(path: String): Socket = AndroidCoreSocket(path)

private class AndroidCoreSocket(private val path: String) : Socket() {
    private val socket = LocalSocket()
    private var readTimeout = 0

    override fun connect(endpoint: SocketAddress, timeout: Int) {
        socket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
        if (readTimeout != 0) {
            socket.soTimeout = readTimeout
        }
    }

    override fun getInputStream() = socket.inputStream

    override fun getOutputStream() = socket.outputStream

    override fun setSoTimeout(timeout: Int) {
        readTimeout = timeout
        if (socket.isConnected) {
            socket.soTimeout = timeout
        }
    }

    override fun getSoTimeout(): Int = readTimeout

    override fun isConnected(): Boolean = socket.isConnected

    override fun close() {
        try {
            socket.close()
        } finally {
            super.close()
        }
    }
}
