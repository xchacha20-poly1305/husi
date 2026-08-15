package fr.husi.core

import fr.husi.ktx.Logs
import fr.husi.libcore.BridgeClient
import fr.husi.libcore.Libcore
import fr.husi.libcore.StreamCall
import fr.husi.libcore.StreamHandler
import fr.husi.proto.daemon.ClashMode
import fr.husi.proto.daemon.ClashModeStatus
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.DefaultLogLevel
import fr.husi.proto.daemon.Groups
import fr.husi.proto.daemon.Log
import fr.husi.proto.daemon.OpenConnectAuthResponseSubmission
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.proto.daemon.OutboundList
import fr.husi.proto.daemon.ServiceStatus
import fr.husi.proto.daemon.StartedAt
import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.Version
import fr.husi.proto.daemon.clashMode
import fr.husi.proto.daemon.closeConnectionRequest
import fr.husi.proto.daemon.openConnectAuthChallengeCancel
import fr.husi.proto.daemon.selectOutboundRequest
import fr.husi.proto.daemon.setGroupExpandRequest
import fr.husi.proto.daemon.subscribeConnectionsRequest
import fr.husi.proto.daemon.subscribeStatusRequest
import fr.husi.proto.daemon.uRLTestRequest as daemonURLTestRequest
import fr.husi.proto.v1.GenerateSchemaResponse
import fr.husi.proto.v1.GetCertMode
import fr.husi.proto.v1.GetCertResponse
import fr.husi.proto.v1.GetClientMetadataResponse
import fr.husi.proto.v1.GetDaemonInfoResponse
import fr.husi.proto.v1.GetVersionResponse
import fr.husi.proto.v1.PluginProcessSpec
import fr.husi.proto.v1.STUNTestResponse
import fr.husi.proto.v1.SchemaKind
import fr.husi.proto.v1.SpeedTestMode
import fr.husi.proto.v1.SpeedTestResponse
import fr.husi.proto.v1.StandaloneURLTestResponse
import fr.husi.proto.v1.StartServiceRequest
import fr.husi.proto.v1.SubscribeServiceEventsResponse
import fr.husi.proto.v1.URLTestOptions
import fr.husi.proto.v1.URLTestResponse
import fr.husi.proto.v1.checkConfigRequest
import fr.husi.proto.v1.claimServiceRequest
import fr.husi.proto.v1.generateSchemaRequest
import fr.husi.proto.v1.getCertRequest
import fr.husi.proto.v1.getClientMetadataRequest
import fr.husi.proto.v1.getDaemonInfoRequest
import fr.husi.proto.v1.getVersionRequest
import fr.husi.proto.v1.resetNetworkRequest
import fr.husi.proto.v1.runTaskRequest
import fr.husi.proto.v1.setStartAtBootRequest
import fr.husi.proto.v1.speedTestRequest
import fr.husi.proto.v1.sTUNTestRequest
import fr.husi.proto.v1.standaloneURLTestRequest
import fr.husi.proto.v1.stopServiceRequest
import fr.husi.proto.v1.subscribeServiceEventsRequest
import fr.husi.proto.v1.takeOverServiceRequest
import fr.husi.proto.v1.uRLTestOptions
import fr.husi.proto.v1.uRLTestRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Typed suspend/Flow surface over the raw [BridgeClient] gRPC bridge.
 *
 * Mirrors the Phase 1 contract: daemon.StartedService for the shared plane,
 * husi.v1.CoreService / ApplicationService / AppService for husi-only RPCs.
 */
interface CoreClient {
    fun subscribeServiceStatus(): Flow<ServiceStatus>
    fun subscribeServiceEvents(): Flow<ServiceEvent>
    fun subscribeStatus(interval: Duration): Flow<Status>
    fun subscribeLog(): Flow<Log>
    suspend fun clearLogs()
    suspend fun getDefaultLogLevel(): DefaultLogLevel
    fun subscribeConnections(interval: Duration): Flow<ConnectionEvents>
    suspend fun closeConnection(id: String)
    suspend fun closeAllConnections()
    fun subscribeGroups(): Flow<Groups>
    fun subscribeOutbounds(): Flow<OutboundList>
    suspend fun selectOutbound(groupTag: String, outboundTag: String)
    suspend fun setGroupExpand(groupTag: String, expand: Boolean)
    suspend fun getClashModeStatus(): ClashModeStatus
    fun subscribeClashMode(): Flow<ClashMode>
    suspend fun setClashMode(mode: String)
    fun subscribeOpenConnectStatus(): Flow<OpenConnectStatusUpdate>
    suspend fun submitOpenConnectAuthResponse(submission: OpenConnectAuthResponseSubmission)
    suspend fun cancelOpenConnectAuthChallenge(endpointTag: String, challengeId: String)
    suspend fun getVersion(): GetVersionResponse
    suspend fun getDaemonVersion(): Version
    suspend fun getStartedAt(): Long
    suspend fun daemonUrlTest(outboundTag: String)
    suspend fun urlTest(
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions = URLTestOptions.getDefaultInstance(),
    ): Int

    suspend fun standaloneUrlTest(
        config: String,
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions = URLTestOptions.getDefaultInstance(),
        plugins: List<PluginProcessSpec> = emptyList(),
    ): Int

    suspend fun checkConfig(config: String)
    suspend fun generateSchema(kind: SchemaKind): String
    suspend fun getCert(
        server: String,
        serverName: String,
        mode: GetCertMode,
        socksProxyUrl: String,
    ): String

    fun stunTest(server: String, socksProxyUrl: String): Flow<STUNTestResponse>
    fun speedTest(
        mode: SpeedTestMode,
        url: String,
        timeoutMs: Int,
        uploadLengthBytes: Long,
        socksProxyUrl: String,
        userAgent: String,
    ): Flow<SpeedTestResponse>

    suspend fun resetNetwork()
    suspend fun runTask(taskId: String)

    // DaemonService (desktop session / daemon host). Android leaves these unused until Phase 4.
    suspend fun getDaemonInfo(): GetDaemonInfoResponse
    suspend fun claimService()
    suspend fun takeOverService()
    suspend fun startService(request: StartServiceRequest)
    suspend fun stopService()
    suspend fun getClientMetadata(): GetClientMetadataResponse
    suspend fun setStartAtBoot(enabled: Boolean)

    suspend fun probe()
    suspend fun close()
}

class CoreRpcException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * JNI-free surface of [BridgeClient] so tests can inject a fake without
 * constructing the generated native type.
 */
internal interface CoreBridge {
    fun callWithTimeout(method: String, request: ByteArray, timeoutMs: Int): ByteArray
    fun stream(method: String, request: ByteArray, handler: StreamHandler): CoreStreamCall
    fun probe()
    fun close()
}

internal fun interface CoreStreamCall {
    fun close()
}

private class JniCoreBridge(private val client: BridgeClient) : CoreBridge {
    override fun callWithTimeout(method: String, request: ByteArray, timeoutMs: Int): ByteArray =
        client.callWithTimeout(method, request, timeoutMs)

    override fun stream(
        method: String,
        request: ByteArray,
        handler: StreamHandler,
    ): CoreStreamCall {
        val call = client.stream(method, request, handler)
        return CoreStreamCall { call.close() }
    }

    override fun probe() = client.probe()

    override fun close() = client.close()
}

class BridgeCoreClient private constructor(
    private val newBridge: () -> CoreBridge,
    private val retryDelay: Duration,
    private val maxRetryDelay: Duration,
    private val stableReset: Duration,
) : CoreClient {
    constructor(
        basePath: String? = null,
        bridgeFactory: (String?) -> BridgeClient = { path ->
            Libcore.newBridgeClient(path)
        },
        retryDelay: Duration = 200.milliseconds,
        maxRetryDelay: Duration = 5.seconds,
        stableReset: Duration = 5.seconds,
    ) : this(
        newBridge = { JniCoreBridge(bridgeFactory(basePath)) },
        retryDelay = retryDelay,
        maxRetryDelay = maxRetryDelay,
        stableReset = stableReset,
    )

    /** Test seam: inject a fake [CoreBridge] instead of the JNI client. */
    internal constructor(
        createBridge: (String?) -> CoreBridge,
        retryDelay: Duration = 200.milliseconds,
        maxRetryDelay: Duration = 5.seconds,
        stableReset: Duration = 5.seconds,
    ) : this(
        newBridge = { createBridge(null) },
        retryDelay = retryDelay,
        maxRetryDelay = maxRetryDelay,
        stableReset = stableReset,
    )

    private val access = Mutex()
    private var bridge: CoreBridge? = null

    private suspend fun getOrCreateBridge(): CoreBridge {
        access.withLock {
            return bridge ?: newBridge().also { bridge = it }
        }
    }

    /**
     * Holds the mutex only while getting/creating the shared [CoreBridge].
     * Invokes [block] outside the lock so long RPCs do not serialize peers.
     * Resets the bridge only on connection-level failures, not per-call status
     * errors (DeadlineExceeded, NotFound, InvalidArgument, …).
     */
    private suspend fun <T> withBridge(block: suspend (CoreBridge) -> T): T {
        val client = getOrCreateBridge()
        try {
            return block(client)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CoreRpcException) {
            resetIfConnectionFailure(client, e)
            throw e
        } catch (e: Exception) {
            val mapped = mapBridgeError(e)
            resetIfConnectionFailure(client, mapped)
            throw mapped
        }
    }

    /** Tears down the shared bridge if [e] indicates a connection-level failure. */
    private suspend fun resetIfConnectionFailure(client: CoreBridge, e: CoreRpcException) {
        if (!isConnectionFailure(e.code)) return
        access.withLock {
            if (bridge === client) resetLocked()
        }
    }

    private fun resetLocked() {
        val current = bridge
        bridge = null
        runCatching { current?.close() }
    }

    /**
     * Closes the shared [BridgeClient]. In-flight [stream] collectors observe
     * onClosed, retry, and call [getOrCreateBridge], which invokes the bridge
     * factory again (re-resolving the socket path on desktop when switching
     * between the session working dir and the daemon).
     */
    override suspend fun close() {
        access.withLock { resetLocked() }
    }

    private suspend fun unary(
        method: String,
        request: ByteArray = EMPTY_PROTO,
        timeout: Duration = DEFAULT_UNARY_TIMEOUT,
    ): ByteArray {
        return withBridge { client ->
            runInterruptible(Dispatchers.IO) {
                try {
                    client.callWithTimeout(
                        method,
                        request,
                        timeout.inWholeMilliseconds.toInt(),
                    )
                } catch (e: Exception) {
                    throw mapBridgeError(e)
                }
            }
        }
    }

    /**
     * Long-lived server stream with retry. Multiplexes on the shared
     * [BridgeClient] from [getOrCreateBridge]; only the [StreamCall] is
     * closed between attempts. Connection-level failures reset that shared
     * bridge. [close] tears it down so in-flight collectors observe onClosed,
     * retry, and redial — the bridge factory re-resolves the socket path on
     * each create (session dir vs daemon).
     */
    private fun <T> stream(
        method: String,
        request: ByteArray = EMPTY_PROTO,
        parse: (ByteArray) -> T,
    ): Flow<T> = callbackFlow {
        var delayDuration = retryDelay
        var activeCall: CoreStreamCall? = null
        val job = launch {
            while (isActive) {
                val client = try {
                    getOrCreateBridge()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logs.w("core client stream create: $method", e)
                    delay(delayDuration)
                    delayDuration = (delayDuration * 2).coerceAtMost(maxRetryDelay)
                    continue
                }
                var call: CoreStreamCall? = null
                val closed = CompletableDeferred<String?>()
                val elapsed = measureTime {
                    try {
                        val handler = object : StreamHandler {
                            override fun onMessage(message: ByteArray?) {
                                if (message == null) return
                                val parsed = try {
                                    parse(message)
                                } catch (e: Exception) {
                                    Logs.w("core client stream parse: $method", e)
                                    return
                                }
                                trySend(parsed)
                            }

                            override fun onClosed(message: String?) {
                                closed.complete(message)
                            }
                        }
                        call = client.stream(method, request, handler)
                        activeCall = call
                        val errMessage = closed.await()
                        if (!errMessage.isNullOrEmpty()) {
                            val mapped = mapBridgeError(Exception(errMessage))
                            resetIfConnectionFailure(client, mapped)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val mapped = mapBridgeError(e)
                        resetIfConnectionFailure(client, mapped)
                        Logs.w("core client stream: $method", e)
                    } finally {
                        activeCall = null
                        runCatching { call?.close() }
                    }
                }
                delayDuration = if (elapsed >= stableReset) {
                    retryDelay
                } else {
                    (delayDuration * 2).coerceAtMost(maxRetryDelay)
                }
                if (isActive) {
                    delay(delayDuration)
                }
            }
        }
        awaitClose {
            job.cancel()
            runCatching { activeCall?.close() }
        }
    }.buffer(Channel.UNLIMITED)

    override fun subscribeServiceStatus(): Flow<ServiceStatus> =
        stream(Methods.SUBSCRIBE_SERVICE_STATUS) { ServiceStatus.parseFrom(it) }

    override fun subscribeServiceEvents(): Flow<ServiceEvent> {
        val request = subscribeServiceEventsRequest { }.toByteArray()
        return stream(Methods.SUBSCRIBE_SERVICE_EVENTS, request) {
            SubscribeServiceEventsResponse.parseFrom(it)
        }.mapNotNull { it.toServiceEvent() }
    }

    override fun subscribeStatus(interval: Duration): Flow<Status> {
        val request = subscribeStatusRequest {
            this.interval = interval.inWholeMilliseconds
        }.toByteArray()
        return stream(Methods.SUBSCRIBE_STATUS, request) { Status.parseFrom(it) }
    }

    override fun subscribeLog(): Flow<Log> =
        stream(Methods.SUBSCRIBE_LOG) { Log.parseFrom(it) }

    override suspend fun clearLogs() {
        unary(Methods.CLEAR_LOGS)
    }

    override suspend fun getDefaultLogLevel(): DefaultLogLevel {
        return DefaultLogLevel.parseFrom(unary(Methods.GET_DEFAULT_LOG_LEVEL))
    }

    override fun subscribeConnections(interval: Duration): Flow<ConnectionEvents> {
        val request = subscribeConnectionsRequest {
            this.interval = interval.inWholeMilliseconds
        }.toByteArray()
        return stream(Methods.SUBSCRIBE_CONNECTIONS, request) { ConnectionEvents.parseFrom(it) }
    }

    override suspend fun closeConnection(id: String) {
        unary(
            Methods.CLOSE_CONNECTION,
            closeConnectionRequest { this.id = id }.toByteArray(),
        )
    }

    override suspend fun closeAllConnections() {
        unary(Methods.CLOSE_ALL_CONNECTIONS)
    }

    override fun subscribeGroups(): Flow<Groups> =
        stream(Methods.SUBSCRIBE_GROUPS) { Groups.parseFrom(it) }

    override fun subscribeOutbounds(): Flow<OutboundList> =
        stream(Methods.SUBSCRIBE_OUTBOUNDS) { OutboundList.parseFrom(it) }

    override suspend fun selectOutbound(groupTag: String, outboundTag: String) {
        unary(
            Methods.SELECT_OUTBOUND,
            selectOutboundRequest {
                this.groupTag = groupTag
                this.outboundTag = outboundTag
            }.toByteArray(),
        )
    }

    override suspend fun setGroupExpand(groupTag: String, expand: Boolean) {
        unary(
            Methods.SET_GROUP_EXPAND,
            setGroupExpandRequest {
                this.groupTag = groupTag
                isExpand = expand
            }.toByteArray(),
        )
    }

    override suspend fun getClashModeStatus(): ClashModeStatus {
        return ClashModeStatus.parseFrom(unary(Methods.GET_CLASH_MODE_STATUS))
    }

    override fun subscribeClashMode(): Flow<ClashMode> =
        stream(Methods.SUBSCRIBE_CLASH_MODE) { ClashMode.parseFrom(it) }

    override suspend fun setClashMode(mode: String) {
        unary(
            Methods.SET_CLASH_MODE,
            clashMode { this.mode = mode }.toByteArray(),
        )
    }

    override fun subscribeOpenConnectStatus(): Flow<OpenConnectStatusUpdate> =
        stream(Methods.SUBSCRIBE_OPENCONNECT_STATUS) { OpenConnectStatusUpdate.parseFrom(it) }

    override suspend fun submitOpenConnectAuthResponse(submission: OpenConnectAuthResponseSubmission) {
        unary(Methods.SUBMIT_OPENCONNECT_AUTH_RESPONSE, submission.toByteArray())
    }

    override suspend fun cancelOpenConnectAuthChallenge(endpointTag: String, challengeId: String) {
        unary(
            Methods.CANCEL_OPENCONNECT_AUTH_CHALLENGE,
            openConnectAuthChallengeCancel {
                this.endpointTag = endpointTag
                challengeID = challengeId
            }.toByteArray(),
        )
    }

    override suspend fun getVersion(): GetVersionResponse {
        return GetVersionResponse.parseFrom(
            unary(Methods.HUSI_GET_VERSION, getVersionRequest { }.toByteArray()),
        )
    }

    override suspend fun getDaemonVersion(): Version {
        return Version.parseFrom(unary(Methods.GET_VERSION))
    }

    override suspend fun getStartedAt(): Long {
        return StartedAt.parseFrom(unary(Methods.GET_STARTED_AT)).startedAt
    }

    override suspend fun daemonUrlTest(outboundTag: String) {
        unary(
            Methods.DAEMON_URL_TEST,
            daemonURLTestRequest { this.outboundTag = outboundTag }.toByteArray(),
        )
    }

    override suspend fun urlTest(
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions,
    ): Int {
        val request = uRLTestRequest {
            outboundTag = tag
            this.link = link
            this.timeoutMs = timeoutMs
            this.options = options
        }.toByteArray()
        val timeout = (timeoutMs + 2000).milliseconds
        return URLTestResponse.parseFrom(unary(Methods.HUSI_URL_TEST, request, timeout)).latencyMs
    }

    override suspend fun standaloneUrlTest(
        config: String,
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions,
        plugins: List<PluginProcessSpec>,
    ): Int {
        val request = standaloneURLTestRequest {
            this.config = config
            outboundTag = tag
            this.link = link
            this.timeoutMs = timeoutMs
            this.options = options
            this.plugins.addAll(plugins)
        }.toByteArray()
        // Host-side plugin startup grace is 500 ms; give the RPC a little headroom
        // beyond the pure network timeout when plugins are involved.
        val pluginHeadroomMs = if (plugins.isEmpty()) 0 else 3000
        val timeout = (timeoutMs + 2000 + pluginHeadroomMs).milliseconds
        return StandaloneURLTestResponse.parseFrom(
            unary(Methods.STANDALONE_URL_TEST, request, timeout),
        ).latencyMs
    }

    override suspend fun checkConfig(config: String) {
        unary(
            Methods.CHECK_CONFIG,
            checkConfigRequest { this.config = config }.toByteArray(),
            30.seconds,
        )
    }

    override suspend fun generateSchema(kind: SchemaKind): String {
        val bytes = unary(
            Methods.GENERATE_SCHEMA,
            generateSchemaRequest { this.kind = kind }.toByteArray(),
            30.seconds,
        )
        return GenerateSchemaResponse.parseFrom(bytes).schema
    }

    override suspend fun getCert(
        server: String,
        serverName: String,
        mode: GetCertMode,
        socksProxyUrl: String,
    ): String {
        val bytes = unary(
            Methods.GET_CERT,
            getCertRequest {
                this.server = server
                this.serverName = serverName
                this.mode = mode
                this.socksProxyUrl = socksProxyUrl
            }.toByteArray(),
            30.seconds,
        )
        return GetCertResponse.parseFrom(bytes).pem
    }

    override fun stunTest(server: String, socksProxyUrl: String): Flow<STUNTestResponse> {
        val request = sTUNTestRequest {
            this.server = server
            this.socksProxyUrl = socksProxyUrl
        }.toByteArray()
        return oneShotStream(Methods.STUN_TEST, request) { STUNTestResponse.parseFrom(it) }
    }

    override fun speedTest(
        mode: SpeedTestMode,
        url: String,
        timeoutMs: Int,
        uploadLengthBytes: Long,
        socksProxyUrl: String,
        userAgent: String,
    ): Flow<SpeedTestResponse> {
        val request = speedTestRequest {
            this.mode = mode
            this.url = url
            this.timeoutMs = timeoutMs
            this.uploadLengthBytes = uploadLengthBytes
            this.socksProxyUrl = socksProxyUrl
            this.userAgent = userAgent
        }.toByteArray()
        return oneShotStream(Methods.SPEED_TEST, request) { SpeedTestResponse.parseFrom(it) }
    }

    /**
     * Server-streaming RPC that ends when the host closes the stream (tool
     * RPCs, not long-lived subscriptions). Does not auto-retry.
     */
    private fun <T> oneShotStream(
        method: String,
        request: ByteArray,
        parse: (ByteArray) -> T,
    ): Flow<T> = callbackFlow {
        var call: CoreStreamCall? = null
        var client: CoreBridge? = null
        try {
            val bridge = getOrCreateBridge()
            client = bridge
            val closed = CompletableDeferred<String?>()
            val handler = object : StreamHandler {
                override fun onMessage(message: ByteArray?) {
                    if (message == null) return
                    val parsed = try {
                        parse(message)
                    } catch (e: Exception) {
                        Logs.w("core client one-shot parse: $method", e)
                        return
                    }
                    trySend(parsed)
                }

                override fun onClosed(message: String?) {
                    closed.complete(message)
                }
            }
            call = runInterruptible(Dispatchers.IO) {
                try {
                    bridge.stream(method, request, handler)
                } catch (e: Exception) {
                    throw mapBridgeError(e)
                }
            }
            val errMessage = closed.await()
            if (!errMessage.isNullOrEmpty()) {
                throw mapBridgeError(Exception(errMessage))
            }
            close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CoreRpcException) {
            client?.let { resetIfConnectionFailure(it, e) }
            close(e)
        } catch (e: Exception) {
            val mapped = mapBridgeError(e)
            client?.let { resetIfConnectionFailure(it, mapped) }
            close(mapped)
        } finally {
            runCatching { call?.close() }
        }
        awaitClose {
            runCatching { call?.close() }
        }
    }.buffer(Channel.UNLIMITED)

    override suspend fun resetNetwork() {
        unary(Methods.RESET_NETWORK, resetNetworkRequest { }.toByteArray())
    }

    override suspend fun runTask(taskId: String) {
        unary(
            Methods.RUN_TASK,
            runTaskRequest { this.taskId = taskId }.toByteArray(),
        )
    }

    override suspend fun getDaemonInfo(): GetDaemonInfoResponse {
        return GetDaemonInfoResponse.parseFrom(
            unary(Methods.GET_DAEMON_INFO, getDaemonInfoRequest { }.toByteArray()),
        )
    }

    override suspend fun claimService() {
        unary(Methods.CLAIM_SERVICE, claimServiceRequest { }.toByteArray())
    }

    override suspend fun takeOverService() {
        unary(Methods.TAKE_OVER_SERVICE, takeOverServiceRequest { }.toByteArray())
    }

    override suspend fun startService(request: StartServiceRequest) {
        unary(Methods.START_SERVICE, request.toByteArray(), 60.seconds)
    }

    override suspend fun stopService() {
        unary(Methods.STOP_SERVICE, stopServiceRequest { }.toByteArray(), 30.seconds)
    }

    override suspend fun getClientMetadata(): GetClientMetadataResponse {
        return GetClientMetadataResponse.parseFrom(
            unary(Methods.GET_CLIENT_METADATA, getClientMetadataRequest { }.toByteArray()),
        )
    }

    override suspend fun setStartAtBoot(enabled: Boolean) {
        unary(
            Methods.SET_START_AT_BOOT,
            setStartAtBootRequest { this.enabled = enabled }.toByteArray(),
        )
    }

    override suspend fun probe() {
        withBridge { client ->
            runInterruptible(Dispatchers.IO) {
                try {
                    client.probe()
                } catch (e: Exception) {
                    throw mapBridgeError(e)
                }
            }
        }
    }

    private object Methods {
        const val GET_VERSION = "/daemon.StartedService/GetVersion"
        const val GET_STARTED_AT = "/daemon.StartedService/GetStartedAt"
        const val DAEMON_URL_TEST = "/daemon.StartedService/URLTest"
        const val SUBSCRIBE_SERVICE_STATUS = "/daemon.StartedService/SubscribeServiceStatus"
        const val SUBSCRIBE_LOG = "/daemon.StartedService/SubscribeLog"
        const val GET_DEFAULT_LOG_LEVEL = "/daemon.StartedService/GetDefaultLogLevel"
        const val CLEAR_LOGS = "/daemon.StartedService/ClearLogs"
        const val SUBSCRIBE_STATUS = "/daemon.StartedService/SubscribeStatus"
        const val SUBSCRIBE_GROUPS = "/daemon.StartedService/SubscribeGroups"
        const val GET_CLASH_MODE_STATUS = "/daemon.StartedService/GetClashModeStatus"
        const val SUBSCRIBE_CLASH_MODE = "/daemon.StartedService/SubscribeClashMode"
        const val SET_CLASH_MODE = "/daemon.StartedService/SetClashMode"
        const val SELECT_OUTBOUND = "/daemon.StartedService/SelectOutbound"
        const val SET_GROUP_EXPAND = "/daemon.StartedService/SetGroupExpand"
        const val SUBSCRIBE_CONNECTIONS = "/daemon.StartedService/SubscribeConnections"
        const val CLOSE_CONNECTION = "/daemon.StartedService/CloseConnection"
        const val CLOSE_ALL_CONNECTIONS = "/daemon.StartedService/CloseAllConnections"
        const val SUBSCRIBE_OUTBOUNDS = "/daemon.StartedService/SubscribeOutbounds"
        const val SUBSCRIBE_OPENCONNECT_STATUS = "/daemon.StartedService/SubscribeOpenConnectStatus"
        const val SUBMIT_OPENCONNECT_AUTH_RESPONSE =
            "/daemon.StartedService/SubmitOpenConnectAuthResponse"
        const val CANCEL_OPENCONNECT_AUTH_CHALLENGE =
            "/daemon.StartedService/CancelOpenConnectAuthChallenge"

        const val HUSI_GET_VERSION = "/husi.v1.CoreService/GetVersion"
        const val HUSI_URL_TEST = "/husi.v1.CoreService/URLTest"
        const val RESET_NETWORK = "/husi.v1.CoreService/ResetNetwork"
        const val SUBSCRIBE_SERVICE_EVENTS = "/husi.v1.CoreService/SubscribeServiceEvents"

        const val CHECK_CONFIG = "/husi.v1.ApplicationService/CheckConfig"
        const val GENERATE_SCHEMA = "/husi.v1.ApplicationService/GenerateSchema"
        const val STANDALONE_URL_TEST = "/husi.v1.ApplicationService/StandaloneURLTest"
        const val GET_CERT = "/husi.v1.ApplicationService/GetCert"
        const val STUN_TEST = "/husi.v1.ApplicationService/STUNTest"
        const val SPEED_TEST = "/husi.v1.ApplicationService/SpeedTest"

        const val RUN_TASK = "/husi.v1.AppService/RunTask"

        const val GET_DAEMON_INFO = "/husi.v1.DaemonService/GetDaemonInfo"
        const val CLAIM_SERVICE = "/husi.v1.DaemonService/ClaimService"
        const val TAKE_OVER_SERVICE = "/husi.v1.DaemonService/TakeOverService"
        const val START_SERVICE = "/husi.v1.DaemonService/StartService"
        const val STOP_SERVICE = "/husi.v1.DaemonService/StopService"
        const val GET_CLIENT_METADATA = "/husi.v1.DaemonService/GetClientMetadata"
        const val SET_START_AT_BOOT = "/husi.v1.DaemonService/SetStartAtBoot"
    }

    companion object {
        private val DEFAULT_UNARY_TIMEOUT = 10.seconds

        /** google.protobuf.Empty serializes to zero bytes. */
        private val EMPTY_PROTO = ByteArray(0)

        /**
         * Codes that indicate the shared bridge is unusable and should be redialed.
         * Per-call status errors (NotFound, DeadlineExceeded, InvalidArgument, …)
         * intentionally do not tear down the connection.
         */
        private val CONNECTION_FAILURE_CODES = setOf(
            "Unavailable",
            "UNAVAILABLE",
        )

        private fun isConnectionFailure(code: String): Boolean =
            code in CONNECTION_FAILURE_CODES

        /**
         * Bridge errors are formatted by [libcore.BridgeClient] as
         * `"<Code>: <message>"` when the failure is a gRPC status (see bridge.go).
         * Transport failures without a status keep their original message and
         * map to code `"Unknown"`.
         */
        private fun mapBridgeError(e: Exception): CoreRpcException {
            if (e is CoreRpcException) return e
            val message = e.message ?: e.toString()
            val separator = message.indexOf(": ")
            if (separator > 0) {
                val maybeCode = message.substring(0, separator)
                if (maybeCode in KNOWN_GRPC_CODES) {
                    return CoreRpcException(
                        maybeCode,
                        message.substring(separator + 2).ifEmpty { message },
                        e,
                    )
                }
            }
            return CoreRpcException("Unknown", message, e)
        }

        private val KNOWN_GRPC_CODES = setOf(
            "OK",
            "Canceled",
            "Unknown",
            "InvalidArgument",
            "DeadlineExceeded",
            "NotFound",
            "AlreadyExists",
            "PermissionDenied",
            "ResourceExhausted",
            "FailedPrecondition",
            "Aborted",
            "OutOfRange",
            "Unimplemented",
            "Internal",
            "Unavailable",
            "DataLoss",
            "Unauthenticated",
        )
    }
}

/** Build URLTestOptions from DataStore-style flags. */
fun urlTestOptions(unifiedDelay: Boolean, ignoreHandshakeTime: Boolean): URLTestOptions {
    return uRLTestOptions {
        this.unifiedDelay = unifiedDelay
        this.ignoreHandshakeTime = ignoreHandshakeTime
    }
}
