@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.database

import androidx.room3.Database
import androidx.room3.ExperimentalRoomApi
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.RoomWarnings
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

@OptIn(ExperimentalRoomApi::class)
@Suppress(RoomWarnings.NO_DATABASE_CONSTRUCTOR)
@Database(entities = [RemoteServerEntity::class], version = 1)
internal abstract class RemoteDatabase : RoomDatabase() {
    abstract fun remoteServerDao(): RemoteServerEntity.Dao

    companion object {
        val instance: RemoteDatabase by lazy {
            Room.databaseBuilder(name = "husi-remote.db", factory = ::RemoteDatabase_Impl)
                .setDriver(WebWorkerSQLiteDriver(createDatabaseWorker()))
                .build()
        }
    }
}

@JsFun("() => new Worker(new URL('sqlite-worker.js', document.baseURI), { type: 'module' })")
private external fun createDatabaseWorker(): Worker
