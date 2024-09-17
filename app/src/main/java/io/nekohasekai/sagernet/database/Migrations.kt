@file:Suppress("ClassName")

package io.nekohasekai.sagernet.database

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "proxy_entities",
        columnName = "nekoBean",
    ),
)
class SagerDatabase_Migration_2_3 : AutoMigrationSpec

object SagerDatabase_Migration_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""ALTER TABLE `rules` ADD `clientType` TEXT NOT NULL DEFAULT ''""")
    }
}

object SagerDatabase_Migration_4_5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""ALTER TABLE `rules` ADD `clashMode` TEXT NOT NULL DEFAULT ''""")
    }
}

@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "proxy_entities",
        columnName = "trojanGoBean",
    ),
    DeleteColumn(
        tableName = "rules",
        columnName = "ruleSet",
    ),
)
class SagerDatabase_Migration_5_6 : AutoMigrationSpec

object SagerDatabase_Migration_6_7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM proxy_entities WHERE type = " + ProxyEntity.TYPE_TROJAN_GO.toString())
        db.execSQL("DELETE FROM proxy_entities WHERE type = " + ProxyEntity.TYPE_NEKO.toString())
    }
}