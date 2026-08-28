package fr.husi.database.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.CoroutineScope
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream

private object MultiProcessPreferenceDataStoreHolder {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context, scope: CoroutineScope): DataStore<Preferences> {
        val appContext = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: MultiProcessDataStoreFactory.create(
                serializer = MultiProcessPreferencesSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                migrations = listOf(RoomToDataStoreMigration(appContext)),
                scope = scope,
                produceFile = { appContext.preferencesDataStoreFile("configuration") },
            ).also { instance = it }
        }
    }
}

private object MultiProcessPreferencesSerializer : Serializer<Preferences> {
    override val defaultValue: Preferences
        get() = PreferencesSerializer.defaultValue

    override suspend fun readFrom(input: InputStream): Preferences {
        return input.source().buffer().use { buffered ->
            PreferencesSerializer.readFrom(buffered)
        }
    }

    override suspend fun writeTo(t: Preferences, output: OutputStream) {
        output.sink().buffer().use { buffered ->
            PreferencesSerializer.writeTo(t, buffered)
            buffered.flush()
        }
    }
}

internal actual fun createPlatformConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> {
    return MultiProcessPreferenceDataStoreHolder.get(resolveAndroidRepository().context, scope)
}
