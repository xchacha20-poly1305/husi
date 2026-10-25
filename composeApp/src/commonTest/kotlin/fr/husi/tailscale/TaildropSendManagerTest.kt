package fr.husi.tailscale

import fr.husi.core.CoreChannel
import fr.husi.core.KurpcCoreClient
import fr.husi.core.TAILDROP_CHUNK_SIZE
import fr.husi.core.remote.CoreTarget
import fr.husi.proto.daemon.TaildropSendClientMessage
import fr.husi.proto.daemon.TaildropSendServerMessage
import fr.husi.proto.daemon.taildropSendProgress
import fr.husi.proto.daemon.taildropSendServerMessage
import io.github.xchacha20_poly1305.kurpc.CallOptions
import io.github.xchacha20_poly1305.kurpc.UnaryResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaildropSendManagerTest {

    @Test
    fun `send with no files does not open a session`() = runTest {
        val manager = TaildropSendManager(runningTarget { ScriptedCoreChannel() }, this)

        val sessionId = manager.send("ts", "peer", "Peer", emptyList())

        assertEquals(TaildropSendManager.NO_SESSION_ID, sessionId)
        assertEquals(emptyList(), manager.sessions.value)
    }

    @Test
    fun `send without a running core fails the session`() = runTest {
        val manager = TaildropSendManager(MutableStateFlow(null), this)

        manager.send("ts", "peer", "Peer", listOf(TaildropSource("a.txt", "a")))
        advanceUntilIdle()

        val session = manager.sessions.value.single()
        assertTrue(session.finished)
        assertEquals("core is not running", session.errorMessage)
    }

    @Test
    fun `send streams start, full sized chunks, and file done for each file`() = runTest {
        assertEquals(16 * 1024 - 64, TAILDROP_CHUNK_SIZE)
        val channel = ScriptedCoreChannel()
        val manager = TaildropSendManager(runningTarget { channel }, this)
        val payload = ByteArray(TAILDROP_CHUNK_SIZE + 3) { index -> index.toByte() }
        val maximumRead = 100

        manager.send(
            tag = "ts",
            peerStableId = "peer-stable",
            peerName = "Laptop",
            files = listOf(
                TaildropSource("big.bin", payload.size.toLong()) {
                    LimitedReadSource(payload, maximumRead)
                },
                TaildropSource("note.txt", "abcd"),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("/daemon.StartedService/SendTaildropFiles"), channel.methods)
        val messages = channel.clientMessages()
        assertEquals(
            listOf(
                "start",
                "chunk:$TAILDROP_CHUNK_SIZE",
                "chunk:3",
                "fileDone",
                "chunk:4",
                "fileDone",
            ),
            messages.map { it.kind() },
        )
        val start = messages.first().start
        assertEquals("ts", start.endpointTag)
        assertEquals("peer-stable", start.peerStableID)
        assertEquals(
            listOf("big.bin" to payload.size.toLong(), "note.txt" to 4L),
            start.filesList.map { it.name to it.size },
        )
        assertContentEquals(payload.copyOfRange(0, TAILDROP_CHUNK_SIZE), messages[1].chunk.data.toByteArray())
        assertContentEquals(
            payload.copyOfRange(TAILDROP_CHUNK_SIZE, payload.size),
            messages[2].chunk.data.toByteArray(),
        )
        assertContentEquals("abcd".encodeToByteArray(), messages[4].chunk.data.toByteArray())

        channel.finish()
        advanceUntilIdle()
        val session = manager.sessions.value.single()
        assertTrue(session.finished)
        assertNull(session.errorMessage)
        assertEquals("Laptop", session.peerName)
    }

    @Test
    fun `progress maps sent bytes and completion and ignores received bytes`() = runTest {
        val channel = ScriptedCoreChannel()
        val manager = TaildropSendManager(runningTarget { channel }, this)
        manager.send(
            tag = "ts",
            peerStableId = "peer-stable",
            peerName = "Laptop",
            files = listOf(
                TaildropSource("hello.txt", TAILDROP_UNKNOWN_SIZE) {
                    Buffer().write("hello".encodeToByteArray())
                },
            ),
        )
        advanceUntilIdle()

        val start = channel.clientMessages().first()
        assertTrue(start.hasStart())
        assertEquals(TAILDROP_UNKNOWN_SIZE, start.start.filesList.single().size)
        assertEquals(TAILDROP_UNKNOWN_SIZE, file(manager).size)
        assertEquals(0, file(manager).sentBytes)

        channel.respond(taildropSendServerMessage { receivedBytes = 50L })
        advanceUntilIdle()
        assertEquals(0L, file(manager).sentBytes)
        assertFalse(file(manager).completed)

        channel.respond(
            taildropSendServerMessage {
                progress = taildropSendProgress {
                    fileIndex = 0
                    sentBytes = 2L
                }
            },
        )
        advanceUntilIdle()
        assertEquals(2L, file(manager).sentBytes)
        assertEquals(TAILDROP_UNKNOWN_SIZE, file(manager).size)
        assertFalse(file(manager).completed)

        channel.respond(
            taildropSendServerMessage {
                progress = taildropSendProgress {
                    fileIndex = 0
                    sentBytes = 5L
                    fileCompleted = true
                }
            },
        )
        advanceUntilIdle()
        assertEquals(5L, file(manager).sentBytes)
        assertEquals(5L, file(manager).size)
        assertTrue(file(manager).completed)
        assertFalse(manager.sessions.value.single().finished)

        channel.respond(
            taildropSendServerMessage {
                progress = taildropSendProgress {
                    fileIndex = 4
                    sentBytes = 99L
                    fileCompleted = true
                }
            },
        )
        advanceUntilIdle()
        assertEquals(5L, file(manager).sentBytes)
        assertEquals(5L, file(manager).size)

        channel.finish()
        advanceUntilIdle()
        assertTrue(manager.sessions.value.single().finished)
        assertNull(manager.sessions.value.single().errorMessage)
    }

    @Test
    fun `cancel stops the upload and removes the session`() = runTest {
        val channel = ScriptedCoreChannel()
        channel.pauseAfterEachRequest = true
        val manager = TaildropSendManager(runningTarget { channel }, this)
        var opened = false
        val sessionId = manager.send(
            tag = "ts",
            peerStableId = "peer-stable",
            peerName = "Laptop",
            files = listOf(
                TaildropSource("big.bin", TAILDROP_CHUNK_SIZE.toLong()) {
                    opened = true
                    Buffer().write(ByteArray(TAILDROP_CHUNK_SIZE))
                },
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("start"), channel.clientMessages().map { it.kind() })
        assertFalse(opened)
        assertEquals(sessionId, manager.sessions.value.single().id)

        manager.cancel(sessionId)
        advanceUntilIdle()
        assertEquals(emptyList(), manager.sessions.value)

        channel.continueRequests.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("start"), channel.clientMessages().map { it.kind() })
        assertFalse(opened)
    }
}

private fun file(manager: TaildropSendManager) = manager.sessions.value.single().files.single()

private fun TaildropSendClientMessage.kind(): String = when {
    hasStart() -> "start"
    hasChunk() -> "chunk:${chunk.data.size()}"
    hasFileDone() -> "fileDone"
    else -> "empty"
}

/** Reads at most [maximumRead] bytes at a time, so a chunk has to be assembled from several reads. */
private class LimitedReadSource(
    private val payload: ByteArray,
    private val maximumRead: Int,
) : Source {
    private var position = 0

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (position >= payload.size) return -1L
        val count = minOf(byteCount, maximumRead.toLong(), (payload.size - position).toLong()).toInt()
        sink.write(payload, position, count)
        position += count
        return count.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}

private class ScriptedCoreChannel : CoreChannel {
    val methods = mutableListOf<String>()
    var pauseAfterEachRequest = false
    val continueRequests = CompletableDeferred<Unit>()
    private val requests = mutableListOf<ByteArray>()
    private val responses = Channel<ByteArray>(Channel.UNLIMITED)

    fun clientMessages(): List<TaildropSendClientMessage> =
        synchronized(requests) { requests.toList() }.map { TaildropSendClientMessage.parseFrom(it) }

    fun respond(message: TaildropSendServerMessage) {
        check(responses.trySend(message.toByteArray()).isSuccess)
    }

    fun finish() {
        responses.close()
    }

    override suspend fun unary(method: String, request: ByteArray, options: CallOptions): ByteArray =
        error("unexpected unary $method")

    override suspend fun unaryCall(method: String, request: ByteArray, options: CallOptions): UnaryResponse =
        error("unexpected unaryCall $method")

    override fun serverStreaming(method: String, request: ByteArray, options: CallOptions): Flow<ByteArray> =
        error("unexpected server stream $method")

    override suspend fun clientStreaming(
        method: String,
        requests: Flow<ByteArray>,
        options: CallOptions,
    ): ByteArray = error("unexpected client stream $method")

    override fun bidiStreaming(method: String, requests: Flow<ByteArray>, options: CallOptions): Flow<ByteArray> = flow {
        methods += method
        coroutineScope {
            val incoming = launch {
                requests.collect { message ->
                    synchronized(this@ScriptedCoreChannel.requests) {
                        this@ScriptedCoreChannel.requests += message
                    }
                    if (pauseAfterEachRequest) continueRequests.await()
                }
            }
            try {
                for (response in responses) emit(response)
            } finally {
                incoming.cancel()
            }
        }
    }

    override fun close() = Unit
}

private fun runningTarget(openChannel: () -> CoreChannel) =
    MutableStateFlow<CoreTarget?>(CoreTarget(KurpcCoreClient(openChannel = openChannel)))
