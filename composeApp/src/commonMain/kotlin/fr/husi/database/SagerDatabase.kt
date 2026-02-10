package fr.husi.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import fr.husi.fmt.KryoConverters

@Database(
    entities = [
        ProxyGroup::class,
        ProxyEntity::class,
        RuleEntity::class,
        AssetEntity::class,
        PluginEntity::class,
    ],
    version = 17,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = SagerDatabase_Migration_2_3::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = SagerDatabase_Migration_5_6::class),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13, spec = SagerDatabase_Migration_12_13::class),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15, spec = SagerDatabase_Migration_14_15::class),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
    ],
)
@TypeConverters(value = [KryoConverters::class])
@ConstructedBy(SagerDatabaseConstructor::class)
abstract class SagerDatabase : RoomDatabase() {

    companion object {

        val instance by lazy { SagerDatabaseProvider.create() }

        val groupDao get() = instance.groupDao()
        val proxyDao get() = instance.proxyDao()
        val rulesDao get() = instance.rulesDao()
        val assetDao get() = instance.assetDao()
        val pluginDao get() = instance.pluginDao()

    }

    abstract fun groupDao(): ProxyGroup.Dao
    abstract fun proxyDao(): ProxyEntity.Dao
    abstract fun rulesDao(): RuleEntity.Dao
    abstract fun assetDao(): AssetEntity.Dao
    abstract fun pluginDao(): PluginEntity.Dao

}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SagerDatabaseConstructor : RoomDatabaseConstructor<SagerDatabase>
