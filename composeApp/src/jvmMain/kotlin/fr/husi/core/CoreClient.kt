package fr.husi.core

import fr.husi.core.KurpcCoreClient.Companion.DEFAULT_UNARY_TIMEOUT
import fr.husi.ktx.Logs
import fr.husi.ktx.invariantPathString
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
import fr.husi.proto.daemon.StartedServiceKurpc
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
import fr.husi.proto.v1.AppServiceKurpc
import fr.husi.proto.v1.ApplicationServiceKurpc
import fr.husi.proto.v1.AttachClientRequest
import fr.husi.proto.v1.CheckConfigRequest
import fr.husi.proto.v1.ClaimServiceRequest
import fr.husi.proto.v1.CoreServiceKurpc
import fr.husi.proto.v1.DaemonServiceKurpc
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
import fr.husi.proto.v1.HTTPFetchRequest
import fr.husi.proto.v1.HTTPFetchResponse
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
import fr.husi.proto.v1.WebDAVDeleteRequest
import fr.husi.proto.v1.WebDAVDownloadRequest
import fr.husi.proto.v1.WebDAVDownloadResponse
import fr.husi.proto.v1.WebDAVListRequest
import fr.husi.proto.v1.WebDAVListResponse
import fr.husi.proto.v1.WebDAVUploadRequest
import io.github.xchacha20_poly1305.kurpc.CallInvoker
import io.github.xchacha20_poly1305.kurpc.CallOptions
import io.github.xchacha20_poly1305.kurpc.ChannelClosedException
import io.github.xchacha20_poly1305.kurpc.ChannelConfig
import io.github.xchacha20_poly1305.kurpc.MethodDescriptor
import io.github.xchacha20_poly1305.kurpc.NativeCallOptions
import io.github.xchacha20_poly1305.kurpc.StatusException
import io.github.xchacha20_poly1305.kurpc.TransportException
import io.github.xchacha20_poly1305.kurpc.bidiStreaming
import io.github.xchacha20_poly1305.kurpc.clientStreaming
import io.github.xchacha20_poly1305.kurpc.serverStreaming
import io.github.xchacha20_poly1305.kurpc.unary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.net.ssl.SSLSocketFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import fr.husi.proto.daemon.URLTestRequest as DaemonURLTestRequest
import io.github.xchacha20_poly1305.kurpc.Channel as RpcChannel
import io.github.xchacha20_poly1305.kurpc.Status as RpcStatus

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

    /** One head message, then the body in chunks. */
    fun httpFetch(request: HTTPFetchRequest): Flow<HTTPFetchResponse>

    suspend fun webdavList(request: WebDAVListRequest): WebDAVListResponse

    /** One head message, then the file in chunks. */
    suspend fun webdavUpload(requests: Flow<WebDAVUploadRequest>)

    /** The file in chunks. */
    fun webdavDownload(request: WebDAVDownloadRequest): Flow<WebDAVDownloadResponse>

    suspend fun webdavDelete(request: WebDAVDeleteRequest)

    /** Latency in milliseconds. [port] is ignored by ICMP. */
    suspend fun ping(protocol: PingProtocol, address: String, port: Int, timeoutMs: Int): Int

    /** File names of the rule sets under [dir] that have a rule matching [keyword], a domain name or IP address. */
    suspend fun matchRuleSets(dir: File, keyword: String): List<String>

    /** PEM of the roots in [store]. */
    suspend fun getRootCertificates(store: RootCertificateStore): String

    /** The Go core of the VPN app in [apkPaths], or null when none is revealed. Android only. */
    suspend fun readAndroidVPNType(apkPaths: List<String>): AndroidVPNType?

    suspend fun detectIP(server: String): String
    suspend fun lookupIPCountry(dir: File, ip: String): String

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

/**
 * One Taildrop upload chunk. Matches sing-box `daemon.TaildropChunkSize`: small enough to stay
 * inside a single gRPC DATA frame.
 */
const val TAILDROP_CHUNK_SIZE = 16 * 1024 - 64

/** A kurpc channel as [KurpcCoreClient] uses it, so tests can inject a fake instead. */
internal interface CoreChannel : CallInvoker, AutoCloseable

private class KurpcCoreChannel(private val channel: RpcChannel) : CoreChannel, CallInvoker by channel {
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

    private suspend fun <Request, Response> unary(
        method: MethodDescriptor<Request, Response>,
        request: Request,
        timeout: Duration = DEFAULT_UNARY_TIMEOUT,
    ): Response = rpc { channel().unary(method, request, CallOptions(timeout = timeout)) }

    private suspend fun <Request, Response> clientStream(
        method: MethodDescriptor<Request, Response>,
        requests: Flow<Request>,
        timeout: Duration,
    ): Response = rpc { channel().clientStreaming(method, requests, CallOptions(timeout = timeout)) }

    /**
     * Long-lived server stream. Whenever the stream ends, cleanly or not, it is opened again
     * after a backoff that resets once a stream has stayed up for [stableReset].
     */
    private fun <Request, Response> stream(
        method: MethodDescriptor<Request, Response>,
        request: Request,
    ): Flow<Response> = flow {
        var delayDuration = retryDelay
        while (true) {
            val opened = TimeSource.Monotonic.markNow()
            try {
                emitAll(
                    channel().serverStreaming(method, request).catch { error ->
                        // Retrying in silence turns a broken stream into an empty panel with
                        // nothing to go on.
                        Logs.w("core client stream: ${method.path}", error.toCoreRpcException())
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Opening the channel failed (a malformed endpoint); `catch` above takes the rest.
                Logs.w("core client stream open: ${method.path}", e)
            }
            delayDuration = if (opened.elapsedNow() >= stableReset) {
                retryDelay
            } else {
                (delayDuration * 2).coerceAtMost(maxRetryDelay)
            }
            delay(delayDuration)
        }
    }.buffer(Channel.UNLIMITED)

    /**
     * Server-streaming RPC that ends when the host closes the stream (tool
     * RPCs, not long-lived subscriptions). Does not auto-retry.
     */
    private fun <Request, Response> oneShotStream(
        method: MethodDescriptor<Request, Response>,
        request: Request,
    ): Flow<Response> = flow {
        emitAll(
            channel().serverStreaming(method, request)
                .catch { error -> throw error.toCoreRpcException() },
        )
    }.buffer(Channel.UNLIMITED)

    /** Bidirectional stream that ends when the host closes it. Does not auto-retry. */
    private fun <Request, Response> bidiStream(
        method: MethodDescriptor<Request, Response>,
        requests: Flow<Request>,
    ): Flow<Response> = flow {
        emitAll(
            channel().bidiStreaming(method, requests)
                .catch { error -> throw error.toCoreRpcException() },
        )
    }.buffer(Channel.UNLIMITED)

    override fun subscribeServiceStatus(): Flow<ServiceStatus> {
        return stream(StartedServiceKurpc.subscribeServiceStatus, Unit)
    }

    override fun subscribeServiceEvents(): Flow<ServiceEvent> {
        return stream(CoreServiceKurpc.subscribeServiceEvents, SubscribeServiceEventsRequest())
            .mapNotNull {
                it.toServiceEvent()
            }
    }

    override fun subscribeStatus(interval: Duration): Flow<Status> {
        val request = SubscribeStatusRequest(interval = interval.inWholeNanoseconds)
        return stream(StartedServiceKurpc.subscribeStatus, request)
    }

    override fun subscribeLog(): Flow<Log> = stream(StartedServiceKurpc.subscribeLog, Unit)

    override suspend fun clearLogs() {
        unary(StartedServiceKurpc.clearLogs, Unit)
    }

    override suspend fun getDefaultLogLevel(): DefaultLogLevel =
        unary(StartedServiceKurpc.getDefaultLogLevel, Unit)

    override fun subscribeConnections(interval: Duration): Flow<ConnectionEvents> {
        val request = SubscribeConnectionsRequest(interval = interval.inWholeNanoseconds)
        return stream(StartedServiceKurpc.subscribeConnections, request)
    }

    override suspend fun closeConnection(id: String) {
        unary(StartedServiceKurpc.closeConnection, CloseConnectionRequest(id = id))
    }

    override suspend fun closeAllConnections() {
        unary(StartedServiceKurpc.closeAllConnections, Unit)
    }

    override fun subscribeGroups(): Flow<Groups> = stream(StartedServiceKurpc.subscribeGroups, Unit)

    override fun subscribeOutbounds(): Flow<OutboundList> = stream(StartedServiceKurpc.subscribeOutbounds, Unit)

    override suspend fun selectOutbound(groupTag: String, outboundTag: String) {
        unary(
            StartedServiceKurpc.selectOutbound,
            SelectOutboundRequest(groupTag = groupTag, outboundTag = outboundTag),
        )
    }

    override suspend fun setGroupExpand(groupTag: String, expand: Boolean) {
        unary(
            StartedServiceKurpc.setGroupExpand,
            SetGroupExpandRequest(groupTag = groupTag, isExpand = expand),
        )
    }

    override suspend fun getClashModeStatus(): ClashModeStatus = unary(StartedServiceKurpc.getClashModeStatus, Unit)

    override fun subscribeClashMode(): Flow<ClashMode> = stream(StartedServiceKurpc.subscribeClashMode, Unit)

    override suspend fun setClashMode(mode: String) {
        unary(StartedServiceKurpc.setClashMode, ClashMode(mode = mode))
    }

    override fun subscribeOpenConnectStatus(): Flow<OpenConnectStatusUpdate> =
        stream(StartedServiceKurpc.subscribeOpenConnectStatus, Unit)

    override suspend fun submitOpenConnectAuthResponse(
        submission: OpenConnectAuthResponseSubmission,
    ) {
        unary(StartedServiceKurpc.submitOpenConnectAuthResponse, submission)
    }

    override suspend fun cancelOpenConnectAuthChallenge(endpointTag: String, challengeId: String) {
        unary(
            StartedServiceKurpc.cancelOpenConnectAuthChallenge,
            OpenConnectAuthChallengeCancel(endpointTag = endpointTag, challengeID = challengeId),
        )
    }

    override fun subscribeOpenVPNStatus(): Flow<OpenVPNStatusUpdate> =
        stream(StartedServiceKurpc.subscribeOpenVPNStatus, Unit)

    override suspend fun submitOpenVPNChallengeResponse(submission: OpenVPNChallengeSubmission) {
        unary(StartedServiceKurpc.submitOpenVPNChallengeResponse, submission)
    }

    override suspend fun cancelOpenVPNChallenge(endpointTag: String, challengeId: String) {
        unary(
            StartedServiceKurpc.cancelOpenVPNChallenge,
            OpenVPNChallengeCancel(endpointTag = endpointTag, challengeID = challengeId),
        )
    }

    override fun subscribeTailscaleStatus(): Flow<TailscaleStatusUpdate> {
        return stream(StartedServiceKurpc.subscribeTailscaleStatus, Unit)
    }

    override fun tailscalePing(tag: String, ip: String): Flow<TailscalePingResponse> {
        val request = TailscalePingRequest(endpointTag = tag, peerIP = ip)
        return oneShotStream(StartedServiceKurpc.startTailscalePing, request)
    }

    override suspend fun setTailscaleExitNode(tag: String, stableId: String) {
        unary(
            StartedServiceKurpc.setTailscaleExitNode,
            SetTailscaleExitNodeRequest(endpointTag = tag, stableID = stableId),
        )
    }

    override suspend fun tailscaleLogout(tag: String) {
        unary(StartedServiceKurpc.tailscaleLogout, TailscaleLogoutRequest(endpointTag = tag))
    }

    override suspend fun getTailscaleCertificate(
        tag: String,
        domain: String,
        minValiditySeconds: Long,
    ): TailscaleCertificate = unary(
        StartedServiceKurpc.getTailscaleCertificate,
        TailscaleCertificateRequest(
            endpointTag = tag,
            domain = domain,
            minValiditySeconds = minValiditySeconds,
        ),
    )

    override fun subscribeTaildropInbox(tag: String): Flow<TaildropInbox> =
        stream(
            StartedServiceKurpc.subscribeTaildropInbox,
            SubscribeTaildropInboxRequest(endpointTag = tag),
        )

    override suspend fun markTaildropInboxRead(tag: String) {
        unary(
            StartedServiceKurpc.markTaildropInboxRead,
            MarkTaildropInboxReadRequest(endpointTag = tag),
        )
    }

    override suspend fun deleteTaildropFile(tag: String, name: String) {
        unary(
            StartedServiceKurpc.deleteTaildropFile,
            DeleteTaildropFileRequest(endpointTag = tag, name = name),
        )
    }

    override suspend fun cancelTaildropReceiving(tag: String, senderId: String, name: String) {
        unary(
            StartedServiceKurpc.cancelTaildropReceiving,
            CancelTaildropReceivingRequest(endpointTag = tag, senderID = senderId, name = name),
        )
    }

    override fun downloadTaildropFile(tag: String, name: String): Flow<DownloadTaildropFileChunk> {
        val request = DownloadTaildropFileRequest(endpointTag = tag, name = name)
        return oneShotStream(StartedServiceKurpc.downloadTaildropFile, request)
    }

    override fun sendTaildropFiles(
        requests: Flow<TaildropSendClientMessage>,
    ): Flow<TaildropSendServerMessage> = bidiStream(StartedServiceKurpc.sendTaildropFiles, requests)

    override fun startTailscaleSSHSession(
        requests: Flow<TailscaleSSHClientMessage>,
    ): Flow<TailscaleSSHServerMessage> = bidiStream(StartedServiceKurpc.startTailscaleSSHSession, requests)

    override suspend fun getVersion(): GetVersionResponse =
        unary(CoreServiceKurpc.getVersion, GetVersionRequest())

    override suspend fun getDaemonVersion(): Version =
        unary(StartedServiceKurpc.getVersion, Unit)

    override suspend fun getStartedAt(): Long =
        unary(StartedServiceKurpc.getStartedAt, Unit).startedAt

    override suspend fun daemonUrlTest(outboundTag: String) {
        unary(StartedServiceKurpc.urlTest, DaemonURLTestRequest(outboundTag = outboundTag))
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
        return unary(CoreServiceKurpc.urlTest, request, timeout).latency_ms
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
        return unary(ApplicationServiceKurpc.standaloneURLTest, request, timeout).latency_ms
    }

    override suspend fun checkConfig(config: String) {
        unary(ApplicationServiceKurpc.checkConfig, CheckConfigRequest(config = config), 30.seconds)
    }

    override suspend fun formatConfig(config: String): String =
        unary(ApplicationServiceKurpc.formatConfig, FormatConfigRequest(config = config)).config

    override suspend fun generateSchema(kind: SchemaKind): String =
        unary(
            ApplicationServiceKurpc.generateSchema,
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
        return unary(ApplicationServiceKurpc.getCert, request, 30.seconds).pem
    }

    override suspend fun webdavList(request: WebDAVListRequest): WebDAVListResponse =
        unary(ApplicationServiceKurpc.webDAVList, request, WEBDAV_TIMEOUT)

    override suspend fun webdavUpload(requests: Flow<WebDAVUploadRequest>) {
        clientStream(ApplicationServiceKurpc.webDAVUpload, requests, WEBDAV_TIMEOUT)
    }

    // A server stream carries no deadline of its own, so the download sets the upload's.
    override fun webdavDownload(request: WebDAVDownloadRequest): Flow<WebDAVDownloadResponse> = flow {
        withTimeout(WEBDAV_TIMEOUT) {
            emitAll(oneShotStream(ApplicationServiceKurpc.webDAVDownload, request))
        }
    }

    override suspend fun webdavDelete(request: WebDAVDeleteRequest) {
        unary(ApplicationServiceKurpc.webDAVDelete, request, WEBDAV_TIMEOUT)
    }

    override suspend fun ping(protocol: PingProtocol, address: String, port: Int, timeoutMs: Int): Int {
        val request = PingRequest(protocol = protocol, address = address, port = port, timeout_ms = timeoutMs)
        return unary(
            ApplicationServiceKurpc.ping, request, timeoutMs.milliseconds + DEFAULT_UNARY_TIMEOUT,
        ).latency_ms
    }

    override suspend fun detectIP(server: String): String =
        unary(CoreServiceKurpc.detectIP, DetectIPRequest(server = server), 15.seconds).ip

    override suspend fun lookupIPCountry(dir: File, ip: String): String {
        val request = LookupIPCountryRequest(ip = ip, directory = dir.invariantPathString())
        return unary(ApplicationServiceKurpc.lookupIPCountry, request, 30.seconds).country_code
    }

    override suspend fun matchRuleSets(dir: File, keyword: String): List<String> {
        val request = MatchRuleSetsRequest(directory = dir.invariantPathString(), keyword = keyword)
        return unary(ApplicationServiceKurpc.matchRuleSets, request, 30.seconds).names
    }

    override suspend fun getRootCertificates(store: RootCertificateStore): String =
        unary(
            ApplicationServiceKurpc.getRootCertificates,
            GetRootCertificatesRequest(store = store),
        ).pem

    override suspend fun readAndroidVPNType(apkPaths: List<String>): AndroidVPNType? {
        val response = unary(
            ApplicationServiceKurpc.readAndroidVPNType,
            ReadAndroidVPNTypeRequest(apk_paths = apkPaths),
        )
        return response.type
    }

    override fun stunTest(server: String, outboundTag: String): Flow<STUNTestProgress> {
        val request = STUNTestRequest(server = server, outboundTag = outboundTag)
        return oneShotStream(StartedServiceKurpc.startSTUNTest, request)
    }

    override fun standaloneStunTest(server: String): Flow<STUNTestProgress> =
        oneShotStream(
            ApplicationServiceKurpc.standaloneSTUNTest,
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
        return oneShotStream(StartedServiceKurpc.startNetworkQualityTest, request)
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
        return oneShotStream(ApplicationServiceKurpc.standaloneNetworkQualityTest, request)
    }

    override fun httpFetch(request: HTTPFetchRequest): Flow<HTTPFetchResponse> =
        oneShotStream(ApplicationServiceKurpc.httpFetch, request)

    override suspend fun resetNetwork() {
        unary(CoreServiceKurpc.resetNetwork, ResetNetworkRequest())
    }

    override suspend fun runTask(taskId: String) {
        unary(AppServiceKurpc.runTask, RunTaskRequest(task_id = taskId))
    }

    override suspend fun getDaemonInfo(): GetDaemonInfoResponse =
        unary(DaemonServiceKurpc.getDaemonInfo, GetDaemonInfoRequest())

    override suspend fun claimService() {
        unary(DaemonServiceKurpc.claimService, ClaimServiceRequest())
    }

    override suspend fun takeOverService() {
        unary(DaemonServiceKurpc.takeOverService, TakeOverServiceRequest(), TAKE_OVER_TIMEOUT)
    }

    override suspend fun startService(request: StartServiceRequest) {
        unary(DaemonServiceKurpc.startService, request, 60.seconds)
    }

    override suspend fun stopService() {
        unary(DaemonServiceKurpc.stopService, StopServiceRequest(), 30.seconds)
    }

    override suspend fun getClientMetadata(): GetClientMetadataResponse =
        unary(DaemonServiceKurpc.getClientMetadata, GetClientMetadataRequest())

    override fun attachClient(): Flow<Unit> =
        stream(DaemonServiceKurpc.attachClient, AttachClientRequest()).map { }

    override suspend fun setStartAtBoot(enabled: Boolean) {
        unary(DaemonServiceKurpc.setStartAtBoot, SetStartAtBootRequest(enabled = enabled))
    }

    /**
     * gRPC health check; any status but SERVING is a failure. Encoded by hand: husi has no
     * health proto for one field.
     */
    override suspend fun probe() {
        val response = rpc {
            // Not wait-for-ready: a probe answers whether a host is there now.
            channel().unary(
                HEALTH_CHECK_METHOD,
                EMPTY_HEALTH_CHECK_REQUEST,
                NativeCallOptions(timeout = DEFAULT_UNARY_TIMEOUT, waitForReady = false),
            )
        }
        val status = healthCheckStatus(response)
        if (status != HEALTH_SERVING) {
            throw CoreRpcException(RpcStatus.Code.UNAVAILABLE, "health status: $status")
        }
    }

    companion object {
        private val DEFAULT_UNARY_TIMEOUT = 10.seconds
        private val WEBDAV_TIMEOUT = 2.minutes

        /**
         * Longer than [DEFAULT_UNARY_TIMEOUT]: take-over blocks on a human
         * typing an administrator password, and ten seconds would cancel
         * the prompt.
         */
        private val TAKE_OVER_TIMEOUT = 10.minutes

        private const val HEALTH_CHECK_METHOD = "/grpc.health.v1.Health/Check"

        /** A `HealthCheckRequest` for the whole server: no fields, zero bytes. */
        private val EMPTY_HEALTH_CHECK_REQUEST = ByteArray(0)

        /** `grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING`. */
        private const val HEALTH_SERVING = 1

        /** Local core at [basePath] (see [localCoreTransport]), resolved again on every [close]. */
        fun local(basePath: () -> String): KurpcCoreClient = KurpcCoreClient(
            openChannel = {
                KurpcCoreChannel(RpcChannel(ChannelConfig(localCoreTransport(basePath()))))
            },
        )

        /**
         * A remote sing-box daemon, as `daemon.NewRemoteClient` dials it. [trust] gives the TLS
         * roots on every dial; see [rootCertificatesSocketFactory].
         */
        fun remote(serverUrl: String, secret: String, trust: () -> SSLSocketFactory): KurpcCoreClient = KurpcCoreClient(
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

/** Build URLTestOptions from DataStore-style flags. */
fun urlTestOptions(unifiedDelay: Boolean, ignoreHandshakeTime: Boolean): URLTestOptions {
    return URLTestOptions(unified_delay = unifiedDelay, ignore_handshake_time = ignoreHandshakeTime)
}
