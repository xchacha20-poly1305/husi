package fr.husi.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import fr.husi.Key
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.absolutePath
import kotlinx.coroutines.Dispatchers

internal actual object SagerDatabaseProvider {
    actual fun create(): SagerDatabase {
        val dbFile = resolveRepository().resolveDatabaseFile(Key.DB_PROFILE)
        return Room.databaseBuilder<SagerDatabase>(name = dbFile.absolutePath())
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
