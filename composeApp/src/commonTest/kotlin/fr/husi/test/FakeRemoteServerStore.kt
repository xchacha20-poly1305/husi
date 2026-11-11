package fr.husi.test

import fr.husi.database.RemoteServer
import fr.husi.database.RemoteServerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRemoteServerStore : RemoteServerStore {
    private val items = MutableStateFlow<List<RemoteServer>>(emptyList())
    private var nextId = 1L
    var writeFailure: Exception? = null

    override fun observe(): Flow<List<RemoteServer>> = items

    override suspend fun save(server: RemoteServer): RemoteServer {
        writeFailure?.let { throw it }
        val existing = items.value.firstOrNull { it.id == server.id }
        val saved = server.copy(
            id = server.id.takeIf { it != 0L } ?: nextId,
            userOrder = if (existing == null && server.userOrder == 0L) {
                (items.value.maxOfOrNull { it.userOrder } ?: 0L) + 1L
            } else server.userOrder,
        )
        nextId = maxOf(nextId, saved.id + 1L)
        items.value = (items.value.filter { it.id != saved.id } + saved)
            .sortedWith(compareBy({ it.userOrder }, { it.id }))
        return saved
    }

    override suspend fun delete(id: Long) {
        writeFailure?.let { throw it }
        items.value = items.value.filter { it.id != id }
    }

    override fun close() = Unit
}
