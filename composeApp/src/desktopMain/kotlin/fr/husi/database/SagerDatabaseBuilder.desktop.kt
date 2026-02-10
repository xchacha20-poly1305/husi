package fr.husi.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import fr.husi.Key
import kotlinx.coroutines.Dispatchers
import java.io.File

internal actual object SagerDatabaseProvider {
    actual fun create(): SagerDatabase {
        val dir = File(System.getProperty("user.home"), ".husi")
        dir.mkdirs()
        val dbFile = File(dir, Key.DB_PROFILE)
        return Room.databaseBuilder<SagerDatabase>(name = dbFile.absolutePath)
            .addMigrations(
                SagerDatabase_Migration_3_4,
                SagerDatabase_Migration_4_5,
                SagerDatabase_Migration_6_7,
            )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
}
