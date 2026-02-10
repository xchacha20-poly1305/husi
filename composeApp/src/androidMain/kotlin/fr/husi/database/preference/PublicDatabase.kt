package fr.husi.database.preference

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import fr.husi.Key
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.repository.androidRepo

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
            val dbFile = androidRepo.getDatabasePath(Key.DB_PUBLIC)
            dbFile.parentFile?.mkdirs()
            return Room.databaseBuilder(
                androidRepo.context,
                PublicDatabase::class.java,
                dbFile.absolutePath,
            )
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .setQueryExecutor { runOnDefaultDispatcher { it.run() } }
                .build()
        }
    }

    abstract fun keyValuePairDao(): KeyValuePair.Dao
}
