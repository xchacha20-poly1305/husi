package fr.husi.database.preference

import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import fr.husi.Key
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.Dispatchers

@Database(entities = [KeyValuePair::class], version = 1)
abstract class PublicDatabase : RoomDatabase() {
    companion object {
        @Volatile
        private var cachedInstance: PublicDatabase? = null

        val instance: PublicDatabase
            get() = cachedInstance ?: synchronized(this) {
                cachedInstance ?: buildDatabase().also { cachedInstance = it }
            }

        val kvPairDao get() = instance.keyValuePairDao()

        fun hasInstance(): Boolean = cachedInstance != null

        fun clearInstance() {
            synchronized(this) {
                cachedInstance = null
            }
        }

        private fun buildDatabase(): PublicDatabase {
            val dbFile = resolveAndroidRepository().resolveDatabaseFile(Key.DB_PUBLIC)
            dbFile.parentFile?.mkdirs()
            return Room.databaseBuilder(
                resolveAndroidRepository().context,
                PublicDatabase::class.java,
                dbFile.absolutePath,
            )
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }

    abstract fun keyValuePairDao(): KeyValuePair.Dao
}
