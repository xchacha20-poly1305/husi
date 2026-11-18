package fr.husi.core

import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcException
import com.squareup.wire.GrpcServerStreamingCall
import com.squareup.wire.GrpcStatus
import com.squareup.wire.GrpcStreamingCall
import fr.husi.ktx.Logs
import fr.husi.ktx.invariantPathString
import fr.husi.ktx.unUrlSafe
import fr.husi.proto.daemon.CancelTaildropReceivingRequest
import fr.husi.proto.daemon.ClashMode
import fr.husi.proto.daemon.ClashModeStatus
import fr.husi.proto.daemon.CloseConnectionRequest
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.DefaultLogLevel
import fr.husi.proto.daemon.DeleteTaildropFileRequest
import fr.husi.proto.daemon.DownloadTaildropFileChunk
import fr.husi.proto.daemon.DownloadTaildropFileRequest
import fr.husi.proto.daemon.Groups
import fr.husi.proto.daemon.GrpcStartedServiceClient
import fr.husi.proto.daemon.Log
import fr.husi.proto.daemon.MarkTaildropInboxReadRequest
import fr.husi.proto.daemon.NetworkQualityTestProgress
import fr.husi.proto.daemon.NetworkQualityTestRequest
import fr.husi.proto.daemon.OpenConnectAuthChallengeCancel
import fr.husi.proto.daemon.OpenConnectAuthResponseSubmission
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.proto.daemon.OpenVPNChallengeCancel
import fr.husi.proto.daemon.OpenVPNChallengeSubmission
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.proto.daemon.OutboundList
import fr.husi.proto.daemon.STUNTestProgress
import fr.husi.proto.daemon.STUNTestRequest
import fr.husi.proto.daemon.SelectOutboundRequest
import fr.husi.proto.daemon.ServiceStatus
import fr.husi.proto.daemon.SetGroupExpandRequest
import fr.husi.proto.daemon.SetTailscaleExitNodeRequest
import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.SubscribeConnectionsRequest
import fr.husi.proto.daemon.SubscribeStatusRequest
import fr.husi.proto.daemon.SubscribeTaildropInboxRequest
import fr.husi.proto.daemon.TaildropInbox
import fr.husi.proto.daemon.TaildropSendClientMessage
import fr.husi.proto.daemon.TaildropSendServerMessage
import fr.husi.proto.daemon.TailscaleCertificate
import fr.husi.proto.daemon.TailscaleCertificateRequest
import fr.husi.proto.daemon.TailscaleLogoutRequest
import fr.husi.proto.daemon.TailscalePingRequest
import fr.husi.proto.daemon.TailscalePingResponse
import fr.husi.proto.daemon.TailscaleSSHClientMessage
import fr.husi.proto.daemon.TailscaleSSHServerMessage
import fr.husi.proto.daemon.TailscaleStatusUpdate
import fr.husi.proto.daemon.Version
import fr.husi.proto.v1.AndroidVPNType
import fr.husi.proto.v1.AttachClientRequest
import fr.husi.proto.v1.CheckConfigRequest
import fr.husi.proto.v1.ClaimServiceRequest
import fr.husi.proto.v1.DecryptAgeRequest
import fr.husi.proto.v1.DecryptAgeResponse
import fr.husi.proto.v1.DetectIPRequest
import fr.husi.proto.v1.FormatConfigRequest
import fr.husi.proto.v1.GenerateSchemaRequest
import fr.husi.proto.v1.GetCertMode
import fr.husi.proto.v1.GetCertRequest
import fr.husi.proto.v1.GetClientMetadataRequest
import fr.husi.proto.v1.GetClientMetadataResponse
import fr.husi.proto.v1.GetDaemonInfoRequest
import fr.husi.proto.v1.GetDaemonInfoResponse
import fr.husi.proto.v1.GetRootCertificatesRequest
import fr.husi.proto.v1.GetVersionRequest
import fr.husi.proto.v1.GetVersionResponse
import fr.husi.proto.v1.GrpcAppServiceClient
import fr.husi.proto.v1.GrpcApplicationServiceClient
import fr.husi.proto.v1.GrpcCoreServiceClient
import fr.husi.proto.v1.GrpcDaemonServiceClient
import fr.husi.proto.v1.LookupIPCountryRequest
import fr.husi.proto.v1.MatchRuleSetsRequest
import fr.husi.proto.v1.PingProtocol
import fr.husi.proto.v1.PingRequest
import fr.husi.proto.v1.PluginProcessSpec
import fr.husi.proto.v1.ReadAndroidVPNTypeRequest
import fr.husi.proto.v1.ResetNetworkRequest
import fr.husi.proto.v1.RootCertificateStore
import fr.husi.proto.v1.RunTaskRequest
import fr.husi.proto.v1.SchemaKind
import fr.husi.proto.v1.SetStartAtBootRequest
import fr.husi.proto.v1.StandaloneNetworkQualityTestRequest
import fr.husi.proto.v1.StandaloneSTUNTestRequest
import fr.husi.proto.v1.StandaloneURLTestRequest
import fr.husi.proto.v1.StartServiceRequest
import fr.husi.proto.v1.StopServiceRequest
import fr.husi.proto.v1.SubscribeServiceEventsRequest
import fr.husi.proto.v1.TakeOverServiceRequest
import fr.husi.proto.v1.URLTestOptions
import fr.husi.proto.v1.URLTestRequest
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.grpc.health.v1.GrpcHealthClient
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import fr.husi.proto.daemon.URLTestRequest as DaemonURLTestRequest

/**
 * Typed suspend/Flow surface over the core's gRPC services.
 *
 * Mirrors the Phase 1 contract: daemon.StartedService for the shared plane, husi.v1.CoreService /
 * ApplicationService / AppService for husi-only RPCs.
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
    fun subscribeTailscaleStatus(): Flow<TailscaleStatusUpdate>
    fun tailscalePing(tag: String, ip: String): Flow<TailscalePingResponse>

    /** [stableId] empty clears the exit node. */
    suspend fun setTailscaleExitNode(tag: String, stableId: String)
    suspend fun tailscaleLogout(tag: String)
    suspend fun getTailscaleCertificate(
        tag: String,
        domain: String,
        minValiditySeconds: Long = 0,
    ): TailscaleCertificate

    fun subscribeTaildropInbox(tag: String): Flow<TaildropInbox>
    suspend fun markTaildropInboxRead(tag: String)
    suspend fun deleteTaildropFile(tag: String, name: String)
    suspend fun cancelTaildropReceiving(tag: String, senderId: String, name: String)

    /**
     * The first message carries the file size. Later messages carry the bytes.
     */
    fun downloadTaildropFile(tag: String, name: String): Flow<DownloadTaildropFileChunk>
    fun sendTaildropFiles(requests: Flow<TaildropSendClientMessage>): Flow<TaildropSendServerMessage>

    /** The first request must be the start message; the rest carry input and window sizes. */
    fun startTailscaleSSHSession(requests: Flow<TailscaleSSHClientMessage>): Flow<TailscaleSSHServerMessage>
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
        options: URLTestOptions = URLTestOptions(),
    ): Int

    suspend fun standaloneUrlTest(
        config: String,
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions = URLTestOptions(),
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

    fun decryptAge(requests: Flow<DecryptAgeRequest>): Flow<DecryptAgeResponse>

    /** Latency in milliseconds. [port] is ignored by ICMP. */
    suspend fun ping(protocol: PingProtocol, address: String, port: Int, timeoutMs: Int): Int

    /** File names of the rule sets under [dir] that have a rule matching [keyword], a domain name or IP address. */
    suspend fun matchRuleSets(dir: PlatformFile, keyword: String): List<String>

    /** PEM of the roots in [store]. */
    suspend fun getRootCertificates(store: RootCertificateStore): String

    /** The Go core of the VPN app in [apkPaths], or null when none is revealed. Android only. */
    suspend fun readAndroidVPNType(apkPaths: List<String>): AndroidVPNType?

    suspend fun detectIP(server: String): String
    suspend fun lookupIPCountry(dir: PlatformFile, ip: String): String

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
    val code: GrpcStatus,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * One Taildrop upload chunk. Matches sing-box `daemon.TaildropChunkSize`: small enough to stay
 * inside a single gRPC DATA frame.
 */
const val TAILDROP_CHUNK_SIZE = 16 * 1024 - 64

internal class CoreChannel(
    grpc: GrpcClient,
    private val closeChannel: () -> Unit,
) : AutoCloseable {
    val started = GrpcStartedServiceClient(grpc)
    val core = GrpcCoreServiceClient(grpc)
    val application = GrpcApplicationServiceClient(grpc)
    val app = GrpcAppServiceClient(grpc)
    val daemon = GrpcDaemonServiceClient(grpc)
    val health = GrpcHealthClient(grpc)

    override fun close() = closeChannel()
}

/** Creates a channel on first use and re-resolves the endpoint after [close]. */
class WireCoreClient internal constructor(
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

    override suspend fun close() {
        access.withLock {
            channel?.close()
            channel = null
        }
    }

    private suspend fun <Request : Any, Response : Any> unary(
        method: CoreChannel.() -> GrpcCall<Request, Response>,
        request: Request,
        timeout: Duration = DEFAULT_UNARY_TIMEOUT,
    ): Response = rpc {
        channel().method().apply {
            this.timeout.timeout(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        }.execute(request)
    }

    /** Reopens a subscription after it ends, with backoff for short-lived attempts. */
    private fun <Request : Any, Response : Any> stream(
        method: CoreChannel.() -> GrpcServerStreamingCall<Request, Response>,
        request: Request,
    ): Flow<Response> = flow {
        var delayDuration = retryDelay
        while (true) {
            val opened = TimeSource.Monotonic.markNow()
            emitAll(
                oneShotStream(method, request).catch { error ->
                    Logs.w("core client subscription", error)
                },
            )
            delayDuration = if (opened.elapsedNow() >= stableReset) {
                retryDelay
            } else {
                (delayDuration * 2).coerceAtMost(maxRetryDelay)
            }
            delay(delayDuration)
        }
    }

    private fun <Request : Any, Response : Any> oneShotStream(
        method: CoreChannel.() -> GrpcServerStreamingCall<Request, Response>,
        request: Request,
        timeout: Duration? = null,
    ): Flow<Response> = flow {
        val call = rpc { channel().method() }
        if (timeout != null) {
            call.timeout.timeout(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        }
        coroutineScope {
            val responses = call.executeIn(this, request)
            try {
                emitAll(
                    responses.receiveAsFlow().catch {
                        throw it.toCoreRpcException()
                    },
                )
            } finally {
                call.cancel()
                responses.cancel()
            }
        }
    }.buffer(Channel.UNLIMITED)

    private fun <Request : Any, Response : Any> bidiStream(
        method: CoreChannel.() -> GrpcStreamingCall<Request, Response>,
        requests: Flow<Request>,
        bufferCapacity: Int = Channel.UNLIMITED,
    ): Flow<Response> = flow {
        val call = rpc { channel().method() }
        coroutineScope {
            val (outbound, inbound) = call.executeIn(this)
            val sender = launch {
                requests.collect {
                    outbound.send(it)
                }
                outbound.close()
            }
            try {
                emitAll(
                    inbound.receiveAsFlow().catch {
                        throw it.toCoreRpcException()
                    },
                )
            } finally {
                call.cancel()
                inbound.cancel()
                sender.cancel()
            }
        }
    }.buffer(bufferCapacity)

    override fun subscribeServiceStatus(): Flow<ServiceStatus> {
        return stream({ started.SubscribeServiceStatus() }, Unit)
    }

    override fun subscribeServiceEvents(): Flow<ServiceEvent> {
        return stream({ core.SubscribeServiceEvents() }, SubscribeServiceEventsRequest())
            .mapNotNull {
                it.toServiceEvent()
            }
    }

    override fun subscribeStatus(interval: Duration): Flow<Status> {
        val request = SubscribeStatusRequest(interval = interval.inWholeNanoseconds)
        return stream({ started.SubscribeStatus() }, request)
    }

    override fun subscribeLog(): Flow<Log> = stream({ started.SubscribeLog() }, Unit)

    override suspend fun clearLogs() {
        unary({ started.ClearLogs() }, Unit)
    }

    override suspend fun getDefaultLogLevel(): DefaultLogLevel =
        unary({ started.GetDefaultLogLevel() }, Unit)

    override fun subscribeConnections(interval: Duration): Flow<ConnectionEvents> {
        val request = SubscribeConnectionsRequest(interval = interval.inWholeNanoseconds)
        return stream({ started.SubscribeConnections() }, request)
    }

    override suspend fun closeConnection(id: String) {
        unary({ started.CloseConnection() }, CloseConnectionRequest(id = id))
    }

    override suspend fun closeAllConnections() {
        unary({ started.CloseAllConnections() }, Unit)
    }

    override fun subscribeGroups(): Flow<Groups> = stream({ started.SubscribeGroups() }, Unit)

    override fun subscribeOutbounds(): Flow<OutboundList> = stream({ started.SubscribeOutbounds() }, Unit)

    override suspend fun selectOutbound(groupTag: String, outboundTag: String) {
        unary(
            { started.SelectOutbound() },
            SelectOutboundRequest(groupTag = groupTag, outboundTag = outboundTag),
        )
    }

    override suspend fun setGroupExpand(groupTag: String, expand: Boolean) {
        unary(
            { started.SetGroupExpand() },
            SetGroupExpandRequest(groupTag = groupTag, isExpand = expand),
        )
    }

    override suspend fun getClashModeStatus(): ClashModeStatus = unary({ started.GetClashModeStatus() }, Unit)

    override fun subscribeClashMode(): Flow<ClashMode> = stream({ started.SubscribeClashMode() }, Unit)

    override suspend fun setClashMode(mode: String) {
        unary({ started.SetClashMode() }, ClashMode(mode = mode))
    }

    override fun subscribeOpenConnectStatus(): Flow<OpenConnectStatusUpdate> =
        stream({ started.SubscribeOpenConnectStatus() }, Unit)

    override suspend fun submitOpenConnectAuthResponse(
        submission: OpenConnectAuthResponseSubmission,
    ) {
        unary({ started.SubmitOpenConnectAuthResponse() }, submission)
    }

    override suspend fun cancelOpenConnectAuthChallenge(endpointTag: String, challengeId: String) {
        unary(
            { started.CancelOpenConnectAuthChallenge() },
            OpenConnectAuthChallengeCancel(endpointTag = endpointTag, challengeID = challengeId),
        )
    }

    override fun subscribeOpenVPNStatus(): Flow<OpenVPNStatusUpdate> =
        stream({ started.SubscribeOpenVPNStatus() }, Unit)

    override suspend fun submitOpenVPNChallengeResponse(submission: OpenVPNChallengeSubmission) {
        unary({ started.SubmitOpenVPNChallengeResponse() }, submission)
    }

    override suspend fun cancelOpenVPNChallenge(endpointTag: String, challengeId: String) {
        unary(
            { started.CancelOpenVPNChallenge() },
            OpenVPNChallengeCancel(endpointTag = endpointTag, challengeID = challengeId),
        )
    }

    override fun subscribeTailscaleStatus(): Flow<TailscaleStatusUpdate> {
        return stream({ started.SubscribeTailscaleStatus() }, Unit)
    }

    override fun tailscalePing(tag: String, ip: String): Flow<TailscalePingResponse> {
        val request = TailscalePingRequest(endpointTag = tag, peerIP = ip)
        return oneShotStream({ started.StartTailscalePing() }, request)
    }

    override suspend fun setTailscaleExitNode(tag: String, stableId: String) {
        unary(
            { started.SetTailscaleExitNode() },
            SetTailscaleExitNodeRequest(endpointTag = tag, stableID = stableId),
        )
    }

    override suspend fun tailscaleLogout(tag: String) {
        unary({ started.TailscaleLogout() }, TailscaleLogoutRequest(endpointTag = tag))
    }

    override suspend fun getTailscaleCertificate(
        tag: String,
        domain: String,
        minValiditySeconds: Long,
    ): TailscaleCertificate = unary(
        { started.GetTailscaleCertificate() },
        TailscaleCertificateRequest(
            endpointTag = tag,
            domain = domain,
            minValiditySeconds = minValiditySeconds,
        ),
    )

    override fun subscribeTaildropInbox(tag: String): Flow<TaildropInbox> =
        stream(
            { started.SubscribeTaildropInbox() },
            SubscribeTaildropInboxRequest(endpointTag = tag),
        )

    override suspend fun markTaildropInboxRead(tag: String) {
        unary(
            { started.MarkTaildropInboxRead() },
            MarkTaildropInboxReadRequest(endpointTag = tag),
        )
    }

    override suspend fun deleteTaildropFile(tag: String, name: String) {
        unary(
            { started.DeleteTaildropFile() },
            DeleteTaildropFileRequest(endpointTag = tag, name = name),
        )
    }

    override suspend fun cancelTaildropReceiving(tag: String, senderId: String, name: String) {
        unary(
            { started.CancelTaildropReceiving() },
            CancelTaildropReceivingRequest(endpointTag = tag, senderID = senderId, name = name),
        )
    }

    override fun downloadTaildropFile(tag: String, name: String): Flow<DownloadTaildropFileChunk> {
        val request = DownloadTaildropFileRequest(endpointTag = tag, name = name)
        return oneShotStream({ started.DownloadTaildropFile() }, request)
    }

    override fun sendTaildropFiles(
        requests: Flow<TaildropSendClientMessage>,
    ): Flow<TaildropSendServerMessage> = bidiStream({ started.SendTaildropFiles() }, requests)

    override fun startTailscaleSSHSession(
        requests: Flow<TailscaleSSHClientMessage>,
    ): Flow<TailscaleSSHServerMessage> = bidiStream({ started.StartTailscaleSSHSession() }, requests)

    override suspend fun getVersion(): GetVersionResponse =
        unary({ core.GetVersion() }, GetVersionRequest())

    override suspend fun getDaemonVersion(): Version =
        unary({ started.GetVersion() }, Unit)

    override suspend fun getStartedAt(): Long =
        unary({ started.GetStartedAt() }, Unit).startedAt

    override suspend fun daemonUrlTest(outboundTag: String) {
        unary({ started.URLTest() }, DaemonURLTestRequest(outboundTag = outboundTag))
    }

    override suspend fun urlTest(
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions,
    ): Int {
        val request = URLTestRequest(
            outbound_tag = tag,
            link = link,
            timeout_ms = timeoutMs,
            options = options,
        )
        val timeout = (timeoutMs + 2000).milliseconds
        return unary({ core.URLTest() }, request, timeout).latency_ms
    }

    override suspend fun standaloneUrlTest(
        config: String,
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions,
        plugins: List<PluginProcessSpec>,
    ): Int {
        val request = StandaloneURLTestRequest(
            config = config,
            outbound_tag = tag,
            link = link,
            timeout_ms = timeoutMs,
            options = options,
            plugins = plugins,
        )
        // Host-side plugin startup grace is 500 ms; give the RPC a little headroom
        // beyond the pure network timeout when plugins are involved.
        val pluginHeadroomMs = if (plugins.isEmpty()) {
            0
        } else {
            3000
        }
        val timeout = (timeoutMs + 2000 + pluginHeadroomMs).milliseconds
        return unary({ application.StandaloneURLTest() }, request, timeout).latency_ms
    }

    override suspend fun checkConfig(config: String) {
        unary({ application.CheckConfig() }, CheckConfigRequest(config = config), 30.seconds)
    }

    override suspend fun formatConfig(config: String): String =
        unary({ application.FormatConfig() }, FormatConfigRequest(config = config)).config

    override suspend fun generateSchema(kind: SchemaKind): String =
        unary(
            { application.GenerateSchema() },
            GenerateSchemaRequest(kind = kind),
            30.seconds,
        ).schema

    override suspend fun getCert(
        server: String,
        serverName: String,
        mode: GetCertMode,
        socksProxyUrl: String,
    ): String {
        val request = GetCertRequest(
            server = server,
            server_name = serverName,
            mode = mode,
            socks_proxy_url = socksProxyUrl,
        )
        return unary({ application.GetCert() }, request, 30.seconds).pem
    }

    override suspend fun ping(
        protocol: PingProtocol,
        address: String,
        port: Int,
        timeoutMs: Int,
    ): Int {
        val request = PingRequest(
            protocol = protocol,
            address = address,
            port = port,
            timeout_ms = timeoutMs,
        )
        return unary(
            { application.Ping() },
            request,
            timeoutMs.milliseconds + DEFAULT_UNARY_TIMEOUT,
        ).latency_ms
    }

    override suspend fun detectIP(server: String): String =
        unary({ core.DetectIP() }, DetectIPRequest(server = server), 15.seconds).ip

    override suspend fun lookupIPCountry(dir: PlatformFile, ip: String): String {
        val request = LookupIPCountryRequest(ip = ip, directory = dir.invariantPathString())
        return unary({ application.LookupIPCountry() }, request, 30.seconds).country_code
    }

    override suspend fun matchRuleSets(dir: PlatformFile, keyword: String): List<String> {
        val request = MatchRuleSetsRequest(directory = dir.invariantPathString(), keyword = keyword)
        return unary({ application.MatchRuleSets() }, request, 30.seconds).names
    }

    override suspend fun getRootCertificates(store: RootCertificateStore): String =
        unary(
            { application.GetRootCertificates() },
            GetRootCertificatesRequest(store = store),
        ).pem

    override suspend fun readAndroidVPNType(apkPaths: List<String>): AndroidVPNType? {
        val response = unary(
            { application.ReadAndroidVPNType() },
            ReadAndroidVPNTypeRequest(apk_paths = apkPaths),
        )
        return response.type
    }

    override fun stunTest(server: String, outboundTag: String): Flow<STUNTestProgress> {
        val request = STUNTestRequest(server = server, outboundTag = outboundTag)
        return oneShotStream({ started.StartSTUNTest() }, request)
    }

    override fun standaloneStunTest(server: String): Flow<STUNTestProgress> =
        oneShotStream(
            { application.StandaloneSTUNTest() },
            StandaloneSTUNTestRequest(server = server),
        )

    override fun networkQualityTest(
        configUrl: String,
        outboundTag: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress> {
        val request = NetworkQualityTestRequest(
            configURL = configUrl,
            outboundTag = outboundTag,
            serial = serial,
            maxRuntimeSeconds = maxRuntimeSeconds,
            http3 = http3,
        )
        return oneShotStream({ started.StartNetworkQualityTest() }, request)
    }

    override fun standaloneNetworkQualityTest(
        configUrl: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress> {
        val request = StandaloneNetworkQualityTestRequest(
            config_url = configUrl,
            serial = serial,
            max_runtime_seconds = maxRuntimeSeconds,
            http3 = http3,
        )
        return oneShotStream({ application.StandaloneNetworkQualityTest() }, request)
    }

    override fun decryptAge(requests: Flow<DecryptAgeRequest>): Flow<DecryptAgeResponse> =
        bidiStream({ application.DecryptAge() }, requests, bufferCapacity = Channel.RENDEZVOUS)

    override suspend fun resetNetwork() {
        unary({ core.ResetNetwork() }, ResetNetworkRequest())
    }

    override suspend fun runTask(taskId: String) {
        unary({ app.RunTask() }, RunTaskRequest(task_id = taskId))
    }

    override suspend fun getDaemonInfo(): GetDaemonInfoResponse =
        unary({ daemon.GetDaemonInfo() }, GetDaemonInfoRequest())

    override suspend fun claimService() {
        unary({ daemon.ClaimService() }, ClaimServiceRequest())
    }

    override suspend fun takeOverService() {
        unary({ daemon.TakeOverService() }, TakeOverServiceRequest(), TAKE_OVER_TIMEOUT)
    }

    override suspend fun startService(request: StartServiceRequest) {
        unary({ daemon.StartService() }, request, 60.seconds)
    }

    override suspend fun stopService() {
        unary({ daemon.StopService() }, StopServiceRequest(), 30.seconds)
    }

    override suspend fun getClientMetadata(): GetClientMetadataResponse =
        unary({ daemon.GetClientMetadata() }, GetClientMetadataRequest())

    override fun attachClient(): Flow<Unit> =
        stream({ daemon.AttachClient() }, AttachClientRequest()).map { }

    override suspend fun setStartAtBoot(enabled: Boolean) {
        unary({ daemon.SetStartAtBoot() }, SetStartAtBootRequest(enabled = enabled))
    }

    override suspend fun probe() {
        val response = unary({ health.Check() }, HealthCheckRequest())
        if (response.status != ServingStatus.SERVING) {
            throw CoreRpcException(GrpcStatus.UNAVAILABLE, "health status: ${response.status}")
        }
    }

    companion object {
        private val DEFAULT_UNARY_TIMEOUT = 10.seconds

        // Take-over can wait for the user to enter an administrator password.
        private val TAKE_OVER_TIMEOUT = 10.minutes

        fun local(basePath: () -> String): WireCoreClient =
            WireCoreClient(openChannel = { openLocalCoreChannel(basePath()) })

        fun remote(serverUrl: String, secret: String, roots: PlatformFile? = null): WireCoreClient =
            WireCoreClient(openChannel = { openRemoteCoreChannel(serverUrl, secret, roots) })
    }
}

/** Normalizes transport and server failures for the UI and CLI. */
private suspend fun <T> rpc(call: suspend () -> T): T = try {
    call()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw e.toCoreRpcException()
}

internal fun Throwable.toCoreRpcException(): CoreRpcException = when (this) {
    is CoreRpcException -> this
    is GrpcException -> CoreRpcException(
        grpcStatus,
        // Wire exposes the grpc-message header verbatim. Its percent escapes are not form encoding.
        decodeGrpcMessage(grpcMessage, grpcStatus.name),
        this,
    )

    is IOException -> CoreRpcException(GrpcStatus.UNAVAILABLE, message ?: toString(), this)

    else -> CoreRpcException(GrpcStatus.UNKNOWN, message ?: toString(), this)
}

private fun decodeGrpcMessage(message: String?, fallback: String): String {
    if (message == null) return fallback
    return if ('%' in message) {
        message.replace("+", "%2B").unUrlSafe()
    } else {
        message
    }
}

/** Build URLTestOptions from DataStore-style flags. */
fun urlTestOptions(unifiedDelay: Boolean, ignoreHandshakeTime: Boolean): URLTestOptions {
    return URLTestOptions(unified_delay = unifiedDelay, ignore_handshake_time = ignoreHandshakeTime)
}
