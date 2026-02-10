package fr.husi.database

import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "plugins")
class PluginEntity(
    @PrimaryKey var pluginId: String = "",
    var path: String = "",
) {

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM plugins")
        fun getAll(): Flow<List<PluginEntity>>

        @Query("SELECT * FROM plugins WHERE pluginId = :pluginId")
        suspend fun getById(pluginId: String): PluginEntity?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun upsert(plugin: PluginEntity)

        @Query("DELETE FROM plugins WHERE pluginId = :pluginId")
        suspend fun delete(pluginId: String): Int
    }
}
