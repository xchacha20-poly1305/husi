package fr.husi.tailscale

import com.google.protobuf.ByteString
import fr.husi.proto.daemon.DownloadTaildropFileChunk
import fr.husi.proto.daemon.downloadTaildropFileChunk
import fr.husi.test.FakeCoreClient
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TaildropFilesTest {

    @Test
    fun `a file already in the receive directory is used without downloading`() = runBlocking {
        val root = tempRoot()
        try {
            val receive = root.resolve("receive")
            receive.mkdirs()
            receive.resolve("note.txt").writeText("local")
            val client = FakeCoreClient()

            val file = materializeTaildropFile(
                coreClient = client,
                receiveDirectory = receive,
                downloadDirectory = root.resolve("downloads"),
                endpointTag = "ts",
                name = "note.txt",
            )

            assertEquals(receive.resolve("note.txt"), file)
            assertEquals("local", file.readText())
            assertNull(client.lastTaildropDownload)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a name that escapes the directory is rejected`() = runBlocking {
        val root = tempRoot()
        try {
            val client = FakeCoreClient()
            assertFailsWith<IllegalStateException> {
                materializeTaildropFile(
                    coreClient = client,
                    receiveDirectory = root.resolve("receive"),
                    downloadDirectory = root.resolve("downloads"),
                    endpointTag = "ts",
                    name = "../secret",
                )
            }
            assertNull(client.lastTaildropDownload)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `download chunks are concatenated after the size chunk`() = runBlocking {
        val root = tempRoot()
        try {
            val client = object : FakeCoreClient() {
                override fun downloadTaildropFile(
                    tag: String,
                    name: String,
                ): Flow<DownloadTaildropFileChunk> {
                    lastTaildropDownload = tag to name
                    return flow {
                        emit(downloadTaildropFileChunk { size = 4L })
                        emit(downloadTaildropFileChunk { data = ByteString.copyFromUtf8("ab") })
                        emit(downloadTaildropFileChunk { data = ByteString.copyFromUtf8("cd") })
                    }
                }
            }
            val downloads = root.resolve("downloads")

            val file = materializeTaildropFile(
                coreClient = client,
                receiveDirectory = null,
                downloadDirectory = downloads,
                endpointTag = "ts",
                name = "note.txt",
            )

            assertEquals("abcd", file.readText())
            assertEquals(downloads.resolve("note.txt"), file)
            assertFalse(downloads.resolve("note.txt$TAILDROP_PARTIAL_SUFFIX").exists())
            assertEquals("ts" to "note.txt", client.lastTaildropDownload)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a download replaces an earlier copy of the same name`() = runBlocking {
        val root = tempRoot()
        try {
            val downloads = root.resolve("downloads")
            downloads.mkdirs()
            downloads.resolve("note.txt").writeText("old")
            val client = object : FakeCoreClient() {
                override fun downloadTaildropFile(
                    tag: String,
                    name: String,
                ): Flow<DownloadTaildropFileChunk> = flow {
                    emit(downloadTaildropFileChunk { size = 3L })
                    emit(downloadTaildropFileChunk { data = ByteString.copyFromUtf8("new") })
                }
            }

            val file = materializeTaildropFile(
                coreClient = client,
                receiveDirectory = null,
                downloadDirectory = downloads,
                endpointTag = "ts",
                name = "note.txt",
            )

            assertEquals("new", file.readText())
        } finally {
            root.deleteRecursively()
        }
    }
}

private fun tempRoot(): File {
    val root = File(System.getProperty("java.io.tmpdir"), "husi-taildrop-${System.nanoTime()}")
    root.mkdirs()
    return root
}
