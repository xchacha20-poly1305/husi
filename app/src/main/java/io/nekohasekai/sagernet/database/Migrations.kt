@file:Suppress("ClassName")

package io.nekohasekai.sagernet.database

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "proxy_entities",
        columnName = "nekoBean"
    )
)
class SagerDatabase_Migration_2_3_Spec : AutoMigrationSpec