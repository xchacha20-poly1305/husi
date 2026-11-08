package fr.husi.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "assets")
class AssetEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var url: String = "",
    var name: String = "",
    @ColumnInfo(defaultValue = "''") var version: String = "",
    @ColumnInfo(defaultValue = "0") var autoUpdateDelay: Int = 0,
    @ColumnInfo(defaultValue = "0") var lastUpdated: Long = 0L,
) {

    @androidx.room3.Dao
    interface Dao {

        @Query("SELECT * FROM assets")
        fun getAll(): Flow<List<AssetEntity>>

        @Query("SELECT * FROM assets WHERE name = :name")
        suspend fun getAllByName(name: String): List<AssetEntity>

        suspend fun get(name: String): AssetEntity? {
            val assets = getAllByName(name)
            if (assets.isEmpty()) return null
            return assets.last()
        }

        @Query("SELECT * FROM assets WHERE id = :id")
        suspend fun getById(id: Long): AssetEntity?

        @Query("DELETE FROM assets WHERE name = :name")
        suspend fun delete(name: String): Int

        @Insert
        suspend fun create0(asset: AssetEntity)

        @Update
        suspend fun update0(asset: AssetEntity)

        suspend fun create(asset: AssetEntity) {
            if (getAllByName(asset.name).isNotEmpty()) {
                delete(asset.name)
            }
            create0(asset)
        }

        suspend fun update(asset: AssetEntity) {
            if (getById(asset.id) != null) {
                update0(asset)
            } else {
                create(asset)
            }
        }

        @Query("DELETE FROM assets")
        suspend fun reset()

        @Insert
        suspend fun insert(assets: List<AssetEntity>)
    }

}
