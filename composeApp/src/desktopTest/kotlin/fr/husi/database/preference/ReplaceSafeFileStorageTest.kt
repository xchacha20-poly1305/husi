package fr.husi.database.preference

import androidx.datastore.core.readData
import androidx.datastore.core.writeData
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds

class ReplaceSafeFileStorageTest {

    private val directory: File = createTempDirectory("replace-safe-storage").toFile()
    private val file = directory.resolve("nested").resolve("configuration.preferences_pb")
    private val groupKey = longPreferencesKey("profileGroup")

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    private fun newConnection() =
        ReplaceSafeFileStorage(PreferencesFileSerializer, file).createConnection()

    @Test
    fun `readData returns default value when file is missing`() = runBlocking {
        val connection = newConnection()

        assertEquals(emptyPreferences(), connection.readData())
    }

    @Test
    fun `writeData creates parent directories and round trips`() = runBlocking {
        val connection = newConnection()

        connection.writeData(preferencesOf(groupKey to 3L))

        assertEquals(3L, connection.readData()[groupKey])
        assertEquals(3L, newConnection().readData()[groupKey])
    }

    @Test
    fun `readData waits for an in-flight write to finish`() = runBlocking {
        val connection = newConnection()
        connection.writeData(preferencesOf(groupKey to 1L))
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()

        val write = launch(Dispatchers.Default) {
            connection.writeScope {
                writeStarted.complete(Unit)
                releaseWrite.await()
                writeData(preferencesOf(groupKey to 2L))
            }
        }
        writeStarted.await()
        val read = async(Dispatchers.Default) { connection.readData()[groupKey] }
        delay(100.milliseconds)
        assertFalse(read.isCompleted)

        releaseWrite.complete(Unit)
        write.join()

        assertEquals(2L, read.await())
    }

    @Test
    fun `writeData leaves no scratch file behind`() = runBlocking {
        val connection = newConnection()

        connection.writeData(preferencesOf(groupKey to 1L))

        assertFalse(file.resolveSibling("${file.name}.tmp").exists())
    }
}
