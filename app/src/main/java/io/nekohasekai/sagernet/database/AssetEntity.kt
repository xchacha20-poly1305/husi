package io.nekohasekai.sagernet.database

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.parcelize.Parcelize

@Entity(tableName = "assets")
@Parcelize
class AssetEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var url: String = "",
    var name: String = ""
) : Parcelable {

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM assets")
        fun getAll(): List<AssetEntity>

        @Query("SELECT * FROM assets WHERE name = :name")
        fun getAllByName(name: String): List<AssetEntity>

        fun get(name: String): AssetEntity? {
            val assets = getAllByName(name)
            if (assets.isEmpty()) return null
            return assets.last()
        }

        @Query("SELECT * FROM assets WHERE id = :id")
        fun getById(id: Long): AssetEntity?

        @Query("DELETE FROM assets WHERE name = :name")
        fun delete(name: String): Int

        @Insert
        fun create0(asset: AssetEntity)

        @Update
        fun update0(asset: AssetEntity)

        fun create(asset: AssetEntity) {
            if (getAllByName(asset.name).isNotEmpty()) {
                delete(asset.name)
            }
            create0(asset)
        }

        fun update(asset: AssetEntity) {
            if (getById(asset.id) != null) {
                update0(asset)
            } else {
                create(asset)
            }
        }

        @Query("DELETE FROM assets")
        fun reset()

        @Insert
        fun insert(assets: List<AssetEntity>)
    }

}