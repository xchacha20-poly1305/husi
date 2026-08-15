package fr.husi.database

import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import fr.husi.libcore.Libcore
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "remote_servers")
class RemoteServerEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var userOrder: Long = 0L,
    var name: String = "",
    var url: String = "",
    var secret: String = "",
) {

    fun toModel(): RemoteServer = RemoteServer(
        id = id,
        userOrder = userOrder,
        name = name,
        url = url,
        secret = secret,
    )

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM remote_servers ORDER BY userOrder, id")
        fun list(): Flow<List<RemoteServerEntity>>

        @Query("SELECT * FROM remote_servers WHERE id = :id")
        suspend fun getById(id: Long): RemoteServerEntity?

        @Query("SELECT COALESCE(MAX(userOrder), 0) + 1 FROM remote_servers")
        suspend fun nextOrder(): Long

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(server: RemoteServerEntity): Long

        @Update
        suspend fun update(server: RemoteServerEntity)

        suspend fun upsert(server: RemoteServerEntity): Long {
            return if (server.id != 0L && getById(server.id) != null) {
                update(server)
                server.id
            } else {
                if (server.userOrder == 0L) {
                    server.userOrder = nextOrder()
                }
                insert(server).also { server.id = it }
            }
        }

        @Query("DELETE FROM remote_servers WHERE id = :id")
        suspend fun delete(id: Long)
    }
}

data class RemoteServer(
    val id: Long = 0L,
    val userOrder: Long = 0L,
    val name: String = "",
    val url: String = "",
    val secret: String = "",
) {
    fun toEntity(): RemoteServerEntity = RemoteServerEntity(
        id = id,
        userOrder = userOrder,
        name = name,
        url = url,
        secret = secret,
    )
}

/**
 * Normalizes a user-entered remote API address into the URL the core dials.
 *
 * Accepts `host`, `host:port` and explicit `http(s)://` addresses; a missing scheme means
 * `http`, and a missing port is left to the core (80 for `http`, 443 for `https`).
 * The stored form is a complete URL, so no caller has to patch a scheme back in.
 *
 * Returns `null` when the address is not a plain `http(s)` endpoint: another scheme, credentials,
 * a path, a query or a fragment all mean the user typed something we would silently ignore.
 */
fun normalizeRemoteServerURL(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val absolute = if (trimmed.contains("://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    val url = runCatching { Libcore.parseURL(absolute) }.getOrNull() ?: return null

    val scheme = url.scheme.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    if (url.path.isNotEmpty() && url.path != "/") return null
    if (url.fragment.isNotEmpty() || absolute.contains('?')) return null
    if (url.host.isEmpty()) return null

    return Libcore.newURL(scheme).also {
        it.fullHost = url.fullHost
    }.string
}
