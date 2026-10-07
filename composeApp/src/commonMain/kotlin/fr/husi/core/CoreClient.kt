package fr.husi.core

import fr.husi.ktx.Logs
import fr.husi.ktx.invariantPathString
import io.github.xchacha20_poly1305.kurpc.CallOptions
import io.github.xchacha20_poly1305.kurpc.Channel as RpcChannel
import io.github.xchacha20_poly1305.kurpc.ChannelClosedException
import io.github.xchacha20_poly1305.kurpc.ChannelConfig
import io.github.xchacha20_poly1305.kurpc.Status as RpcStatus
import io.github.xchacha20_poly1305.kurpc.StatusException
import io.github.xchacha20_poly1305.kurpc.TransportException
import fr.husi.proto.daemon.ClashMode
import fr.husi.proto.daemon.ClashModeStatus
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.DefaultLogLevel
import fr.husi.proto.daemon.Groups
import fr.husi.proto.daemon.Log
import fr.husi.proto.daemon.NetworkQualityTestProgress
import fr.husi.proto.daemon.OpenConnectAuthResponseSubmission
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.proto.daemon.OpenVPNChallengeSubmission
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.proto.daemon.OutboundList
import fr.husi.proto.daemon.STUNTestProgress
import fr.husi.proto.daemon.ServiceStatus
import fr.husi.proto.daemon.StartedAt
import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.Version
import fr.husi.proto.daemon.clashMode
import fr.husi.proto.daemon.closeConnectionRequest
import fr.husi.proto.daemon.networkQualityTestRequest
import fr.husi.proto.daemon.openConnectAuthChallengeCancel
import fr.husi.proto.daemon.openVPNChallengeCancel
import fr.husi.proto.daemon.sTUNTestRequest
import fr.husi.proto.daemon.selectOutboundRequest
import fr.husi.proto.daemon.setGroupExpandRequest
import fr.husi.proto.daemon.subscribeConnectionsRequest
import fr.husi.proto.daemon.subscribeStatusRequest
import fr.husi.proto.daemon.uRLTestRequest as daemonURLTestRequest
import fr.husi.proto.v1.FormatConfigResponse
import fr.husi.proto.v1.GenerateSchemaResponse
import fr.husi.proto.v1.GetCertMode
import fr.husi.proto.v1.GetCertResponse
import fr.husi.proto.v1.GetRootCertificatesResponse
import fr.husi.proto.v1.AndroidVPNType
import fr.husi.proto.v1.ReadAndroidVPNTypeResponse
import fr.husi.proto.v1.MatchRuleSetsResponse
import fr.husi.proto.v1.PingProtocol
import fr.husi.proto.v1.PingResponse
import fr.husi.proto.v1.RootCertificateStore
import fr.husi.proto.v1.GetClientMetadataResponse
import fr.husi.proto.v1.GetDaemonInfoResponse
import fr.husi.proto.v1.GetVersionResponse
import fr.husi.proto.v1.HTTPFetchRequest
import fr.husi.proto.v1.HTTPFetchResponse
import fr.husi.proto.v1.PluginProcessSpec
import fr.husi.proto.v1.SchemaKind
import fr.husi.proto.v1.StandaloneURLTestResponse
import fr.husi.proto.v1.StartServiceRequest
import fr.husi.proto.v1.SubscribeServiceEventsResponse
import fr.husi.proto.v1.URLTestOptions
import fr.husi.proto.v1.URLTestResponse
import fr.husi.proto.v1.attachClientRequest
import fr.husi.proto.v1.checkConfigRequest
import fr.husi.proto.v1.claimServiceRequest
import fr.husi.proto.v1.formatConfigRequest
import fr.husi.proto.v1.generateSchemaRequest
import fr.husi.proto.v1.getCertRequest
import fr.husi.proto.v1.getRootCertificatesRequest
import fr.husi.proto.v1.readAndroidVPNTypeRequest
import fr.husi.proto.v1.matchRuleSetsRequest
import fr.husi.proto.v1.pingRequest
import fr.husi.proto.v1.getClientMetadataRequest
import fr.husi.proto.v1.getDaemonInfoRequest
import fr.husi.proto.v1.getVersionRequest
import fr.husi.proto.v1.resetNetworkRequest
import fr.husi.proto.v1.runTaskRequest
import fr.husi.proto.v1.setStartAtBootRequest
import fr.husi.proto.v1.standaloneNetworkQualityTestRequest
import fr.husi.proto.v1.standaloneSTUNTestRequest
import fr.husi.proto.v1.standaloneURLTestRequest
import fr.husi.proto.v1.stopServiceRequest
import fr.husi.proto.v1.subscribeServiceEventsRequest
import fr.husi.proto.v1.takeOverServiceRequest
import fr.husi.proto.v1.uRLTestOptions
import fr.husi.proto.v1.uRLTestRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.net.ssl.SSLSocketFactory
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Typed suspend/Flow surface over the core's gRPC services.
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
    fun subscribeOpenVPNStatus(): Flow<OpenVPNStatusUpdate>
    suspend fun submitOpenVPNChallengeResponse(submission: OpenVPNChallengeSubmission)
    suspend fun cancelOpenVPNChallenge(endpointTag: String, challengeId: String)
    suspend fun getVersion(): GetVersionResponse
    suspend fun getDaemonVersion(): Version
    suspend fun getStartedAt(): Long

    /**
     * Kept separate from [urlTest] because StartedService is wire-compatible
     * with a vanilla sing-box host and cannot grow husi-only fields (link,
     * timeout, how latency is counted, a synchronous delay). Remote control
     * and the CLI use this; a host without `husi.v1.CoreService` has nothing
     * else.
     */
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
    suspend fun formatConfig(config: String): String
    suspend fun generateSchema(kind: SchemaKind): String
    suspend fun getCert(
        server: String,
        serverName: String,
        mode: GetCertMode,
        socksProxyUrl: String,
    ): String

    /**
     * [standaloneStunTest] / [standaloneNetworkQualityTest] dials directly and is all a host
     * with no service started can answer.
     */

    fun stunTest(server: String, outboundTag: String): Flow<STUNTestProgress>
    fun standaloneStunTest(server: String): Flow<STUNTestProgress>
    fun networkQualityTest(
        configUrl: String,
        outboundTag: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress>

    fun standaloneNetworkQualityTest(
        configUrl: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress>

    /** One head message, then the body in chunks. */
    fun httpFetch(request: HTTPFetchRequest): Flow<HTTPFetchResponse>

    /** Latency in milliseconds. [port] is ignored by ICMP. */
    suspend fun ping(protocol: PingProtocol, address: String, port: Int, timeoutMs: Int): Int

    /** File names of the rule sets under [dir] that have a rule matching [keyword], a domain name or IP address. */
    suspend fun matchRuleSets(dir: File, keyword: String): List<String>

    /** PEM of the roots in [store]. */
    suspend fun getRootCertificates(store: RootCertificateStore): String

    /** The Go core of the VPN app in [apkPaths], or null when none is revealed. Android only. */
    suspend fun readAndroidVPNType(apkPaths: List<String>): AndroidVPNType?

    suspend fun resetNetwork()
    suspend fun runTask(taskId: String)

    // DaemonService (desktop session / daemon host). Android leaves these unused until Phase 4.
    suspend fun getDaemonInfo(): GetDaemonInfoResponse
    suspend fun claimService()
    suspend fun takeOverService()

    fun attachClient(): Flow<Unit>
    suspend fun startService(request: StartServiceRequest)
    suspend fun stopService()
    suspend fun getClientMetadata(): GetClientMetadataResponse
    suspend fun setStartAtBoot(enabled: Boolean)

    suspend fun probe()
    suspend fun close()
}

class CoreRpcException(
    val code: RpcStatus.Code,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The calls [KurpcCoreClient] makes, so tests can inject a fake instead of a kurpc channel. */
internal interface CoreChannel {
    suspend fun unary(method: String, request: ByteArray, options: CallOptions): ByteArray
    fun serverStreaming(method: String, request: ByteArray): Flow<ByteArray>
    fun close()
}

private class KurpcCoreChannel(private val channel: RpcChannel) : CoreChannel {
    override suspend fun unary(method: String, request: ByteArray, options: CallOptions): ByteArray =
        channel.unary(method, request, options)

    // Waits for the socket like the unary calls: a subscription opened while the host is still
    // starting should connect, not fail and back off.
    override fun serverStreaming(method: String, request: ByteArray): Flow<ByteArray> =
        channel.serverStreaming(method, request, CallOptions(waitForReady = true))

    override fun close() = channel.close()
}

/**
 * [CoreClient] over one kurpc [RpcChannel], created on first use by [openChannel].
 *
 * The channel reconnects by itself after the host restarts, so a failed call never replaces it.
 * [close] does: the next call opens a new channel, which re-resolves the endpoint (desktop
 * switches between the session working dir and the daemon this way).
 */
class KurpcCoreClient internal constructor(
    private val openChannel: () -> CoreChannel,
    private val retryDelay: Duration = 200.milliseconds,
    private val maxRetryDelay: Duration = 5.seconds,
    private val stableReset: Duration = 5.seconds,
) : CoreClient {
    private val access = Mutex()
    private var channel: CoreChannel? = null

    private suspend fun channel(): CoreChannel = access.withLock {
        channel ?: openChannel().also { channel = it }
    }

    /**
     * In-flight calls on the old channel fail with [ChannelClosedException]; subscriptions
     * retry on a new one.
     */
    override suspend fun close() {
        access.withLock {
            channel?.close()
            channel = null
        }
    }

    private suspend fun unary(
        method: String,
        request: ByteArray = EMPTY_PROTO,
        timeout: Duration = DEFAULT_UNARY_TIMEOUT,
    ): ByteArray = rpc { channel().unary(method, request, CallOptions(timeout = timeout)) }

    /**
     * Long-lived server stream. Whenever the stream ends, cleanly or not, it is opened again
     * after a backoff that resets once a stream has stayed up for [stableReset].
     */
    private fun <T> stream(
        method: String,
        request: ByteArray = EMPTY_PROTO,
        parse: (ByteArray) -> T,
    ): Flow<T> = flow {
        var delayDuration = retryDelay
        while (true) {
            val opened = TimeSource.Monotonic.markNow()
            try {
                emitAll(
                    channel().serverStreaming(method, request).parseEach(method, parse).catch { error ->
                        // Retrying in silence turns a broken stream into an empty panel with
                        // nothing to go on.
                        Logs.w("core client stream: $method", error.toCoreRpcException())
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Opening the channel failed (a malformed endpoint); `catch` above takes the rest.
                Logs.w("core client stream open: $method", e)
            }
            delayDuration = if (opened.elapsedNow() >= stableReset) {
                retryDelay
            } else {
                (delayDuration * 2).coerceAtMost(maxRetryDelay)
            }
            delay(delayDuration)
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
            this.interval = interval.inWholeNanoseconds
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
            this.interval = interval.inWholeNanoseconds
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

    override fun subscribeOpenVPNStatus(): Flow<OpenVPNStatusUpdate> =
        stream(Methods.SUBSCRIBE_OPENVPN_STATUS) { OpenVPNStatusUpdate.parseFrom(it) }

    override suspend fun submitOpenVPNChallengeResponse(submission: OpenVPNChallengeSubmission) {
        unary(Methods.SUBMIT_OPENVPN_CHALLENGE_RESPONSE, submission.toByteArray())
    }

    override suspend fun cancelOpenVPNChallenge(endpointTag: String, challengeId: String) {
        unary(
            Methods.CANCEL_OPENVPN_CHALLENGE,
            openVPNChallengeCancel {
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

    override suspend fun formatConfig(config: String): String {
        val bytes = unary(
            Methods.FORMAT_CONFIG,
            formatConfigRequest { this.config = config }.toByteArray(),
        )
        return FormatConfigResponse.parseFrom(bytes).config
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

    override suspend fun ping(protocol: PingProtocol, address: String, port: Int, timeoutMs: Int): Int {
        val bytes = unary(
            Methods.PING,
            pingRequest {
                this.protocol = protocol
                this.address = address
                this.port = port
                this.timeoutMs = timeoutMs
            }.toByteArray(),
            timeoutMs.milliseconds + DEFAULT_UNARY_TIMEOUT,
        )
        return PingResponse.parseFrom(bytes).latencyMs
    }

    override suspend fun matchRuleSets(dir: File, keyword: String): List<String> {
        val bytes = unary(
            Methods.MATCH_RULE_SETS,
            matchRuleSetsRequest {
                directory = dir.invariantPathString()
                this.keyword = keyword
            }.toByteArray(),
            30.seconds,
        )
        return MatchRuleSetsResponse.parseFrom(bytes).namesList
    }

    override suspend fun getRootCertificates(store: RootCertificateStore): String {
        val bytes = unary(
            Methods.GET_ROOT_CERTIFICATES,
            getRootCertificatesRequest { this.store = store }.toByteArray(),
        )
        return GetRootCertificatesResponse.parseFrom(bytes).pem
    }

    override suspend fun readAndroidVPNType(apkPaths: List<String>): AndroidVPNType? {
        val bytes = unary(
            Methods.READ_ANDROID_VPN_TYPE,
            readAndroidVPNTypeRequest { this.apkPaths.addAll(apkPaths) }.toByteArray(),
        )
        val response = ReadAndroidVPNTypeResponse.parseFrom(bytes)
        return if (response.hasType()) response.type else null
    }

    override fun stunTest(server: String, outboundTag: String): Flow<STUNTestProgress> {
        val request = sTUNTestRequest {
            this.server = server
            this.outboundTag = outboundTag
        }.toByteArray()
        return oneShotStream(Methods.START_STUN_TEST, request) { STUNTestProgress.parseFrom(it) }
    }

    override fun standaloneStunTest(server: String): Flow<STUNTestProgress> {
        val request = standaloneSTUNTestRequest {
            this.server = server
        }.toByteArray()
        return oneShotStream(Methods.STANDALONE_STUN_TEST, request) {
            STUNTestProgress.parseFrom(it)
        }
    }

    override fun networkQualityTest(
        configUrl: String,
        outboundTag: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress> {
        val request = networkQualityTestRequest {
            this.configURL = configUrl
            this.outboundTag = outboundTag
            this.serial = serial
            this.maxRuntimeSeconds = maxRuntimeSeconds
            this.http3 = http3
        }.toByteArray()
        return oneShotStream(Methods.START_NETWORK_QUALITY_TEST, request) {
            NetworkQualityTestProgress.parseFrom(it)
        }
    }

    override fun standaloneNetworkQualityTest(
        configUrl: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress> {
        val request = standaloneNetworkQualityTestRequest {
            this.configUrl = configUrl
            this.serial = serial
            this.maxRuntimeSeconds = maxRuntimeSeconds
            this.http3 = http3
        }.toByteArray()
        return oneShotStream(Methods.STANDALONE_NETWORK_QUALITY_TEST, request) {
            NetworkQualityTestProgress.parseFrom(it)
        }
    }

    override fun httpFetch(request: HTTPFetchRequest): Flow<HTTPFetchResponse> =
        oneShotStream(Methods.HTTP_FETCH, request.toByteArray()) { HTTPFetchResponse.parseFrom(it) }

    /**
     * Server-streaming RPC that ends when the host closes the stream (tool
     * RPCs, not long-lived subscriptions). Does not auto-retry.
     */
    private fun <T> oneShotStream(
        method: String,
        request: ByteArray,
        parse: (ByteArray) -> T,
    ): Flow<T> = flow {
        emitAll(
            channel().serverStreaming(method, request)
                .parseEach(method, parse)
                .catch { error -> throw error.toCoreRpcException() },
        )
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
        unary(Methods.TAKE_OVER_SERVICE, takeOverServiceRequest { }.toByteArray(), TAKE_OVER_TIMEOUT)
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

    override fun attachClient(): Flow<Unit> =
        stream(Methods.ATTACH_CLIENT, attachClientRequest { }.toByteArray()) { }

    override suspend fun setStartAtBoot(enabled: Boolean) {
        unary(
            Methods.SET_START_AT_BOOT,
            setStartAtBootRequest { this.enabled = enabled }.toByteArray(),
        )
    }

    /** gRPC health check; any status but SERVING is a failure. */
    override suspend fun probe() {
        val response = rpc {
            // Not wait-for-ready: a probe answers whether a host is there now.
            channel().unary(Methods.HEALTH_CHECK, EMPTY_PROTO, CallOptions(timeout = DEFAULT_UNARY_TIMEOUT, waitForReady = false))
        }
        val status = healthCheckStatus(response)
        if (status != HEALTH_SERVING) {
            throw CoreRpcException(RpcStatus.Code.UNAVAILABLE, "health status: $status")
        }
    }

    private object Methods {
        const val HEALTH_CHECK = "/grpc.health.v1.Health/Check"
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
        const val START_STUN_TEST = "/daemon.StartedService/StartSTUNTest"
        const val START_NETWORK_QUALITY_TEST = "/daemon.StartedService/StartNetworkQualityTest"
        const val SUBSCRIBE_OPENCONNECT_STATUS = "/daemon.StartedService/SubscribeOpenConnectStatus"
        const val SUBMIT_OPENCONNECT_AUTH_RESPONSE =
            "/daemon.StartedService/SubmitOpenConnectAuthResponse"
        const val CANCEL_OPENCONNECT_AUTH_CHALLENGE =
            "/daemon.StartedService/CancelOpenConnectAuthChallenge"
        const val SUBSCRIBE_OPENVPN_STATUS = "/daemon.StartedService/SubscribeOpenVPNStatus"
        const val SUBMIT_OPENVPN_CHALLENGE_RESPONSE =
            "/daemon.StartedService/SubmitOpenVPNChallengeResponse"
        const val CANCEL_OPENVPN_CHALLENGE = "/daemon.StartedService/CancelOpenVPNChallenge"

        const val HUSI_GET_VERSION = "/husi.v1.CoreService/GetVersion"
        const val HUSI_URL_TEST = "/husi.v1.CoreService/URLTest"
        const val RESET_NETWORK = "/husi.v1.CoreService/ResetNetwork"
        const val SUBSCRIBE_SERVICE_EVENTS = "/husi.v1.CoreService/SubscribeServiceEvents"

        const val CHECK_CONFIG = "/husi.v1.ApplicationService/CheckConfig"
        const val FORMAT_CONFIG = "/husi.v1.ApplicationService/FormatConfig"
        const val GENERATE_SCHEMA = "/husi.v1.ApplicationService/GenerateSchema"
        const val STANDALONE_URL_TEST = "/husi.v1.ApplicationService/StandaloneURLTest"
        const val GET_CERT = "/husi.v1.ApplicationService/GetCert"
        const val STANDALONE_STUN_TEST = "/husi.v1.ApplicationService/StandaloneSTUNTest"
        const val STANDALONE_NETWORK_QUALITY_TEST =
            "/husi.v1.ApplicationService/StandaloneNetworkQualityTest"
        const val HTTP_FETCH = "/husi.v1.ApplicationService/HTTPFetch"
        const val PING = "/husi.v1.ApplicationService/Ping"
        const val MATCH_RULE_SETS = "/husi.v1.ApplicationService/MatchRuleSets"
        const val GET_ROOT_CERTIFICATES = "/husi.v1.ApplicationService/GetRootCertificates"
        const val READ_ANDROID_VPN_TYPE = "/husi.v1.ApplicationService/ReadAndroidVPNType"

        const val RUN_TASK = "/husi.v1.AppService/RunTask"

        const val GET_DAEMON_INFO = "/husi.v1.DaemonService/GetDaemonInfo"
        const val CLAIM_SERVICE = "/husi.v1.DaemonService/ClaimService"
        const val ATTACH_CLIENT = "/husi.v1.DaemonService/AttachClient"
        const val TAKE_OVER_SERVICE = "/husi.v1.DaemonService/TakeOverService"
        const val START_SERVICE = "/husi.v1.DaemonService/StartService"
        const val STOP_SERVICE = "/husi.v1.DaemonService/StopService"
        const val GET_CLIENT_METADATA = "/husi.v1.DaemonService/GetClientMetadata"
        const val SET_START_AT_BOOT = "/husi.v1.DaemonService/SetStartAtBoot"
    }

    companion object {
        private val DEFAULT_UNARY_TIMEOUT = 10.seconds

        /**
         * Longer than [DEFAULT_UNARY_TIMEOUT]: take-over blocks on a human
         * typing an administrator password, and ten seconds would cancel
         * the prompt.
         */
        private val TAKE_OVER_TIMEOUT = 10.minutes

        /** google.protobuf.Empty serializes to zero bytes. */
        private val EMPTY_PROTO = ByteArray(0)

        /** `grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING`. */
        private const val HEALTH_SERVING = 1

        /** Local core at [basePath] (see [localCoreTransport]), resolved again on every [close]. */
        fun local(basePath: () -> String): KurpcCoreClient =
            KurpcCoreClient(openChannel = { KurpcCoreChannel(RpcChannel(ChannelConfig(localCoreTransport(basePath())))) })

        /**
         * A remote sing-box daemon, as `daemon.NewRemoteClient` dials it. [trust] gives the TLS
         * roots on every dial; see [rootCertificatesSocketFactory].
         */
        fun remote(serverUrl: String, secret: String, trust: () -> SSLSocketFactory): KurpcCoreClient =
            KurpcCoreClient(
                openChannel = {
                    KurpcCoreChannel(RpcChannel(remoteCoreChannelConfig(serverUrl, secret, trust)))
                },
            )
    }
}

/** Runs one call, reporting every kurpc failure as a [CoreRpcException]. */
private suspend fun <T> rpc(call: suspend () -> T): T = try {
    call()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw e.toCoreRpcException()
}

internal fun Throwable.toCoreRpcException(): CoreRpcException = when (this) {
    is CoreRpcException -> this
    is StatusException -> CoreRpcException(code, description, this)
    // No status from a server: it was never reached or went away, as grpc-go reports it.
    is TransportException, is ChannelClosedException ->
        CoreRpcException(RpcStatus.Code.UNAVAILABLE, message ?: toString(), this)
    else -> CoreRpcException(RpcStatus.Code.UNKNOWN, message ?: toString(), this)
}

/** Parses each message; one that does not parse is logged and skipped, not fatal. */
private fun <T> Flow<ByteArray>.parseEach(method: String, parse: (ByteArray) -> T): Flow<T> =
    transform { message ->
        val parsed = try {
            parse(message)
        } catch (e: Exception) {
            Logs.w("core client parse: $method", e)
            return@transform
        }
        emit(parsed)
    }

/** Build URLTestOptions from DataStore-style flags. */
fun urlTestOptions(unifiedDelay: Boolean, ignoreHandshakeTime: Boolean): URLTestOptions {
    return uRLTestOptions {
        this.unifiedDelay = unifiedDelay
        this.ignoreHandshakeTime = ignoreHandshakeTime
    }
}
