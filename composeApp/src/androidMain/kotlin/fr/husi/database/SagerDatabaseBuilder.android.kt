package fr.husi.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import fr.husi.Key
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.Dispatchers

internal actual object SagerDatabaseProvider {
    actual fun create(): SagerDatabase {
        val dbFile = resolveAndroidRepository().resolveDatabaseFile(Key.DB_PROFILE)
        dbFile.parentFile?.mkdirs()
        return Room.databaseBuilder<SagerDatabase>(
            context = resolveAndroidRepository().context,
            name = dbFile.absolutePath,
        )
            .addMigrations(
                SagerDatabase_Migration_3_4,
                SagerDatabase_Migration_4_5,
                SagerDatabase_Migration_6_7,
            )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
}
