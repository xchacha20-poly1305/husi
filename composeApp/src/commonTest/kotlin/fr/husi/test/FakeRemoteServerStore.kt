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

    override suspend fun replaceAll(servers: List<RemoteServer>): List<RemoteServer> {
        writeFailure?.let { throw it }
        for (server in servers) {
            require(server.id >= 0L && server.userOrder >= 0L) { "Invalid server ID or order" }
        }
        var nextOrder = servers.maxOfOrNull { it.userOrder } ?: 0L
        val normalized = servers.map { server ->
            if (server.userOrder == 0L) {
                server.copy(userOrder = ++nextOrder)
            } else {
                server
            }
        }
        val usedIds = mutableSetOf<Long>()
        val prepared = normalized.map { server ->
            val keepId = server.id > 0L && usedIds.add(server.id)
            server to keepId
        }
        val ordered = prepared.filter { it.second } + prepared.filterNot { it.second }
        val inserted = mutableListOf<RemoteServer>()
        for ((server, keepId) in ordered) {
            val saved = if (keepId) {
                nextId = maxOf(nextId, server.id + 1L)
                server
            } else {
                server.copy(id = nextId++)
            }
            inserted.add(saved)
        }
        val sorted = inserted.sortedWith(compareBy({ it.userOrder }, { it.id }))
        items.value = sorted
        return sorted
    }

    override suspend fun delete(id: Long) {
        writeFailure?.let { throw it }
        items.value = items.value.filter { it.id != id }
    }

    override fun close() = Unit
}
