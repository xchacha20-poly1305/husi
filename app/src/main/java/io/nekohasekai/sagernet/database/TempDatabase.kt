package io.nekohasekai.sagernet.database

import androidx.room.Room
import androidx.room.RoomDatabase
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher

@androidx.room.Database(entities = [KeyValuePair::class], version = 1)
abstract class TempDatabase : RoomDatabase() {

    companion object {
        @Suppress("EXPERIMENTAL_API_USAGE")
        private val instance by lazy {
            Room.inMemoryDatabaseBuilder(app, TempDatabase::class.java)
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .setQueryExecutor { runOnDefaultDispatcher { it.run() } }
                .build()
        }

        val profileCacheDao get() = instance.profileCacheDao()

    }

    abstract fun profileCacheDao(): KeyValuePair.Dao
}