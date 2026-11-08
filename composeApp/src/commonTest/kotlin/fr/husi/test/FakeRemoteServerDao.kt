package fr.husi.test

import fr.husi.database.RemoteServerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRemoteServerDao : RemoteServerEntity.Dao {
    private val items = MutableStateFlow<List<RemoteServerEntity>>(emptyList())
    private var nextId = 1L

    override fun list(): Flow<List<RemoteServerEntity>> = items

    override suspend fun getById(id: Long): RemoteServerEntity? =
        items.value.firstOrNull { it.id == id }

    override suspend fun nextOrder(): Long =
        (items.value.maxOfOrNull { it.userOrder } ?: 0L) + 1L

    override suspend fun insert(server: RemoteServerEntity): Long {
        if (server.id == 0L) {
            server.id = nextId++
        } else {
            nextId = maxOf(nextId, server.id + 1L)
        }
        items.value = items.value.filter { it.id != server.id } + server
        return server.id
    }

    override suspend fun update(server: RemoteServerEntity) {
        items.value = items.value.map { current ->
            if (current.id == server.id) server else current
        }
    }

    override suspend fun delete(id: Long) {
        items.value = items.value.filter { it.id != id }
    }
}
