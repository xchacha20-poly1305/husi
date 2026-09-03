package fr.husi.test

import fr.husi.core.CoreClient
import fr.husi.core.ServiceEvent
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
import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.Version
import fr.husi.proto.v1.GetCertMode
import fr.husi.proto.v1.GetClientMetadataResponse
import fr.husi.proto.v1.GetDaemonInfoResponse
import fr.husi.proto.v1.GetVersionResponse
import fr.husi.proto.v1.PluginProcessSpec
import fr.husi.proto.v1.SchemaKind
import fr.husi.proto.v1.StartServiceRequest
import fr.husi.proto.v1.URLTestOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * Hand-rolled [CoreClient] for tests. Override individual methods or push into
 * the shared flows; unimplemented unary RPCs throw.
 */
open class FakeCoreClient : CoreClient {
    val groupsFlow = MutableSharedFlow<Groups>(replay = 1, extraBufferCapacity = 16)
    val connectionsFlow = MutableSharedFlow<ConnectionEvents>(replay = 1, extraBufferCapacity = 16)
    val statusFlow = MutableSharedFlow<Status>(replay = 1, extraBufferCapacity = 16)
    val serviceStatusFlow = MutableSharedFlow<ServiceStatus>(replay = 1, extraBufferCapacity = 16)
    val serviceEventsFlow =
        MutableSharedFlow<ServiceEvent>(replay = 1, extraBufferCapacity = 16)
    val logFlow = MutableSharedFlow<Log>(replay = 1, extraBufferCapacity = 16)
    val openConnectFlow =
        MutableSharedFlow<OpenConnectStatusUpdate>(replay = 1, extraBufferCapacity = 16)
    val openVPNFlow =
        MutableSharedFlow<OpenVPNStatusUpdate>(replay = 1, extraBufferCapacity = 16)

    var closed: Boolean = false
        private set

    override fun subscribeServiceStatus(): Flow<ServiceStatus> = serviceStatusFlow
    override fun subscribeServiceEvents(): Flow<ServiceEvent> = serviceEventsFlow
    override fun subscribeStatus(interval: Duration): Flow<Status> = statusFlow
    override fun subscribeLog(): Flow<Log> = logFlow
    override suspend fun clearLogs() = Unit
    override suspend fun getDefaultLogLevel(): DefaultLogLevel =
        DefaultLogLevel.getDefaultInstance()

    override fun subscribeConnections(interval: Duration): Flow<ConnectionEvents> = connectionsFlow
    override suspend fun closeConnection(id: String) = Unit
    override suspend fun closeAllConnections() = Unit
    override fun subscribeGroups(): Flow<Groups> = groupsFlow
    override fun subscribeOutbounds(): Flow<OutboundList> = emptyFlow()
    override suspend fun selectOutbound(groupTag: String, outboundTag: String) = Unit
    override suspend fun setGroupExpand(groupTag: String, expand: Boolean) = Unit
    override suspend fun getClashModeStatus(): ClashModeStatus =
        ClashModeStatus.getDefaultInstance()

    override fun subscribeClashMode(): Flow<ClashMode> = emptyFlow()
    override suspend fun setClashMode(mode: String) = Unit
    override fun subscribeOpenConnectStatus(): Flow<OpenConnectStatusUpdate> = openConnectFlow
    override suspend fun submitOpenConnectAuthResponse(submission: OpenConnectAuthResponseSubmission) =
        Unit

    override suspend fun cancelOpenConnectAuthChallenge(endpointTag: String, challengeId: String) =
        Unit

    override fun subscribeOpenVPNStatus(): Flow<OpenVPNStatusUpdate> = openVPNFlow
    override suspend fun submitOpenVPNChallengeResponse(submission: OpenVPNChallengeSubmission) {
        lastOpenVPNSubmission = submission
        submitOpenVPNThrowable?.let { throw it }
    }

    override suspend fun cancelOpenVPNChallenge(endpointTag: String, challengeId: String) {
        lastOpenVPNCancel = endpointTag to challengeId
        cancelOpenVPNThrowable?.let { throw it }
    }

    var lastOpenVPNSubmission: OpenVPNChallengeSubmission? = null
    var lastOpenVPNCancel: Pair<String, String>? = null
    var submitOpenVPNThrowable: Throwable? = null
    var cancelOpenVPNThrowable: Throwable? = null

    override suspend fun getVersion(): GetVersionResponse = GetVersionResponse.getDefaultInstance()
    override suspend fun getDaemonVersion(): Version = nextDaemonVersion
    override suspend fun getStartedAt(): Long = nextStartedAt
    override suspend fun daemonUrlTest(outboundTag: String) {
        lastDaemonUrlTestTag = outboundTag
        daemonUrlTestCalls += 1
    }

    var nextDaemonVersion: Version = Version.getDefaultInstance()
    var nextStartedAt: Long = 0L
    var lastDaemonUrlTestTag: String? = null
    var daemonUrlTestCalls: Int = 0
    var probeCalls: Int = 0
    var probeThrowable: Throwable? = null

    override suspend fun urlTest(
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions,
    ): Int = 0

    override suspend fun standaloneUrlTest(
        config: String,
        tag: String,
        link: String,
        timeoutMs: Int,
        options: URLTestOptions,
        plugins: List<PluginProcessSpec>,
    ): Int = 0

    override suspend fun checkConfig(config: String) = Unit
    override suspend fun generateSchema(kind: SchemaKind): String = "{}"
    override suspend fun getCert(
        server: String,
        serverName: String,
        mode: GetCertMode,
        socksProxyUrl: String,
    ): String = ""

    /**
     * [standalone] records which half of the pair answered, so a test can tell
     * the running-service RPC from the direct-dialling one.
     */
    data class StunTestCall(
        val server: String,
        val outboundTag: String,
        val standalone: Boolean,
    )

    var lastStunTest: StunTestCall? = null
    var stunTestCalls: Int = 0
    var stunTestProgresses: List<STUNTestProgress> = emptyList()
    var stunTestThrowable: Throwable? = null

    override fun stunTest(server: String, outboundTag: String): Flow<STUNTestProgress> =
        recordStunTest(StunTestCall(server, outboundTag, standalone = false))

    override fun standaloneStunTest(server: String): Flow<STUNTestProgress> =
        recordStunTest(StunTestCall(server, outboundTag = "", standalone = true))

    private fun recordStunTest(call: StunTestCall): Flow<STUNTestProgress> {
        stunTestCalls += 1
        lastStunTest = call
        return replay(stunTestProgresses, stunTestThrowable)
    }

    data class NetworkQualityTestCall(
        val configUrl: String,
        val outboundTag: String,
        val serial: Boolean,
        val maxRuntimeSeconds: Int,
        val http3: Boolean,
        val standalone: Boolean,
    )

    var lastNetworkQualityTest: NetworkQualityTestCall? = null
    var networkQualityTestCalls: Int = 0
    var networkQualityTestProgresses: List<NetworkQualityTestProgress> = emptyList()
    var networkQualityTestThrowable: Throwable? = null

    override fun networkQualityTest(
        configUrl: String,
        outboundTag: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress> = recordNetworkQualityTest(
        NetworkQualityTestCall(
            configUrl = configUrl,
            outboundTag = outboundTag,
            serial = serial,
            maxRuntimeSeconds = maxRuntimeSeconds,
            http3 = http3,
            standalone = false,
        ),
    )

    override fun standaloneNetworkQualityTest(
        configUrl: String,
        serial: Boolean,
        maxRuntimeSeconds: Int,
        http3: Boolean,
    ): Flow<NetworkQualityTestProgress> = recordNetworkQualityTest(
        NetworkQualityTestCall(
            configUrl = configUrl,
            outboundTag = "",
            serial = serial,
            maxRuntimeSeconds = maxRuntimeSeconds,
            http3 = http3,
            standalone = true,
        ),
    )

    private fun recordNetworkQualityTest(
        call: NetworkQualityTestCall,
    ): Flow<NetworkQualityTestProgress> {
        networkQualityTestCalls += 1
        lastNetworkQualityTest = call
        return replay(networkQualityTestProgresses, networkQualityTestThrowable)
    }

    private fun <T> replay(messages: List<T>, throwable: Throwable?): Flow<T> = flow {
        for (message in messages) {
            emit(message)
        }
        if (throwable != null) {
            throw throwable
        }
    }

    override suspend fun resetNetwork() = Unit
    override suspend fun runTask(taskId: String) = Unit
    var nextDaemonInfo: GetDaemonInfoResponse = GetDaemonInfoResponse.getDefaultInstance()
    var claimServiceCalls: Int = 0
    var takeOverServiceCalls: Int = 0
    var takeOverServiceThrowable: Throwable? = null

    override suspend fun getDaemonInfo(): GetDaemonInfoResponse = nextDaemonInfo

    override suspend fun claimService() {
        claimServiceCalls += 1
    }

    /** How many [attachClient] leases are being collected right now. */
    var attachedClients: Int = 0
        private set

    override fun attachClient(): Flow<Unit> = flow {
        attachedClients += 1
        try {
            emit(Unit)
            awaitCancellation()
        } finally {
            attachedClients -= 1
        }
    }

    override suspend fun takeOverService() {
        takeOverServiceThrowable?.let { throw it }
        takeOverServiceCalls += 1
    }
    var startServiceCalls: Int = 0
    var stopServiceCalls: Int = 0

    /** Set to make [stopService] fail, standing in for an unresponsive host. */
    var stopServiceThrowable: Throwable? = null

    /** Set to make [stopService] hang, standing in for a wedged box instance. */
    var stopServiceDelay: Duration = Duration.ZERO

    override suspend fun startService(request: StartServiceRequest) {
        startServiceCalls += 1
    }

    override suspend fun stopService() {
        stopServiceCalls += 1
        delay(stopServiceDelay)
        stopServiceThrowable?.let { throw it }
    }
    var nextClientMetadata: GetClientMetadataResponse =
        GetClientMetadataResponse.getDefaultInstance()

    override suspend fun getClientMetadata(): GetClientMetadataResponse = nextClientMetadata

    override suspend fun setStartAtBoot(enabled: Boolean) = Unit

    override suspend fun probe() {
        probeCalls += 1
        probeThrowable?.let { throw it }
    }

    override suspend fun close() {
        closed = true
    }
}
