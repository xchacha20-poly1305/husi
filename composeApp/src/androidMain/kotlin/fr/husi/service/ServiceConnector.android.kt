package fr.husi.service

import fr.husi.bg.SagerConnection
import fr.husi.repository.androidRepo

actual object ServiceConnector {
    actual val connectionIdMainActivityForeground: Int =
        SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND
    actual val connectionIdMainActivityBackground: Int =
        SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND
    private val connection =
        SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)

    actual fun connect() {
        connection.connect(androidRepo.context)
    }

    actual fun disconnect() {
        connection.disconnect(androidRepo.context)
    }

    actual fun reconnect() {
        connection.reconnect(androidRepo.context)
    }

    actual fun updateConnectionId(id: Int) {
        connection.updateConnectionId(id)
    }
}
