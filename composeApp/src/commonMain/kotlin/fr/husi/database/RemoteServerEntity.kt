package fr.husi.database

import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
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

    @androidx.room3.Dao
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

        @Transaction
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
