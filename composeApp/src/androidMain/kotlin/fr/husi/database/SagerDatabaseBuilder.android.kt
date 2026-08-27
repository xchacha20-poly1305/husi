package fr.husi.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import fr.husi.Key
import fr.husi.ktx.invariantPathString
import fr.husi.repository.resolveAndroidRepository
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.parent
import kotlinx.coroutines.Dispatchers

internal actual object SagerDatabaseProvider {
    actual fun create(): SagerDatabase {
        val dbFile = resolveAndroidRepository().resolveDatabaseFile(Key.DB_PROFILE)
        dbFile.parent()?.createDirectories()
        return Room.databaseBuilder<SagerDatabase>(
            context = resolveAndroidRepository().context,
            name = dbFile.invariantPathString(),
        )
            .addMigrations(
                SagerDatabase_Migration_3_4,
                SagerDatabase_Migration_4_5,
                SagerDatabase_Migration_6_7,
            )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
}
