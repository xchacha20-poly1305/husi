package fr.husi.database

import kotlinx.coroutines.flow.Flow

interface RemoteServerStore : AutoCloseable {
    fun observe(): Flow<List<RemoteServer>>
    suspend fun save(server: RemoteServer): RemoteServer
    suspend fun delete(id: Long)
}
