package fr.husi.database

import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "plugins")
class PluginEntity(
    @PrimaryKey var pluginId: String = "",
    var path: String = "",
) {

    @androidx.room3.Dao
    interface Dao {

        @Query("SELECT * FROM plugins")
        fun getAll(): Flow<List<PluginEntity>>

        @Query("SELECT * FROM plugins WHERE pluginId = :pluginId")
        suspend fun getById(pluginId: String): PluginEntity?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun upsert(plugin: PluginEntity)

        @Query("DELETE FROM plugins WHERE pluginId = :pluginId")
        suspend fun delete(pluginId: String): Int

        @Query("DELETE FROM plugins")
        suspend fun reset()
    }
}
