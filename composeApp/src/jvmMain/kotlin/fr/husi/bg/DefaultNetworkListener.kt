package fr.husi.bg

expect object DefaultNetworkListener {
    suspend fun start(key: Any, listener: suspend () -> Unit)
    suspend fun stop(key: Any)
}
