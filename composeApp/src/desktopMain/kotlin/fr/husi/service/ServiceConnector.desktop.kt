package fr.husi.service

actual object ServiceConnector {
    actual val connectionIdMainActivityForeground: Int = 2
    actual val connectionIdMainActivityBackground: Int = 3

    actual fun connect() = Unit

    actual fun disconnect() = Unit

    actual fun reconnect() = Unit

    actual fun updateConnectionId(id: Int) = Unit
}
