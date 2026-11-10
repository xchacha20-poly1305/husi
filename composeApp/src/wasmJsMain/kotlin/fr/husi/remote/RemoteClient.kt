package fr.husi.remote

import fr.husi.proto.daemon.StartedServiceClient
import fr.husi.proto.daemon.Version

/** Owns the browser's current API session. Generated clients and their streams belong to that session. */
internal interface RemoteClient {
    val service: StartedServiceClient

    suspend fun connect(url: String, secret: String): Version

    fun close()

    /** Asks [url] for its version without touching the current session. */
    suspend fun testConnection(url: String, secret: String): String

    fun normalizeUrl(value: String): String
}
