package fr.husi.service

expect object ServiceConnector {
    val connectionIdMainActivityForeground: Int
    val connectionIdMainActivityBackground: Int
    fun connect()
    fun disconnect()
    fun reconnect()
    fun updateConnectionId(id: Int)
}
