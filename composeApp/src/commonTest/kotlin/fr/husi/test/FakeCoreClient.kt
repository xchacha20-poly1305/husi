package fr.husi.test

import fr.husi.core.CoreClient
import fr.husi.core.ServiceEvent
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
import fr.husi.proto.daemon.Status
import fr.husi.proto.daemon.Version
import fr.husi.proto.v1.GetCertMode
import fr.husi.proto.v1.GetClientMetadataResponse
import fr.husi.proto.v1.GetDaemonInfoResponse
import fr.husi.proto.v1.GetVersionResponse
import fr.husi.proto.v1.PluginProcessSpec
import fr.husi.proto.v1.STUNTestResponse
import fr.husi.proto.v1.SchemaKind
import fr.husi.proto.v1.SpeedTestMode
import fr.husi.proto.v1.SpeedTestResponse
import fr.husi.proto.v1.StartServiceRequest
import fr.husi.proto.v1.URLTestOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
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

    override fun stunTest(server: String, socksProxyUrl: String): Flow<STUNTestResponse> = emptyFlow()

    data class SpeedTestCall(
        val mode: SpeedTestMode,
        val url: String,
        val timeoutMs: Int,
        val uploadLengthBytes: Long,
        val socksProxyUrl: String,
        val userAgent: String,
    )

    var lastSpeedTest: SpeedTestCall? = null
    var speedTestCalls: Int = 0
    var speedTestResponses: List<SpeedTestResponse> = listOf(
        SpeedTestResponse.newBuilder()
            .setBytesPerSec(1_024_000)
            .setProgress(1.0)
            .setBytesTransferred(1_024_000)
            .build(),
    )
    var speedTestThrowable: Throwable? = null

    override fun speedTest(
        mode: SpeedTestMode,
        url: String,
        timeoutMs: Int,
        uploadLengthBytes: Long,
        socksProxyUrl: String,
        userAgent: String,
    ): Flow<SpeedTestResponse> {
        speedTestCalls += 1
        lastSpeedTest = SpeedTestCall(
            mode = mode,
            url = url,
            timeoutMs = timeoutMs,
            uploadLengthBytes = uploadLengthBytes,
            socksProxyUrl = socksProxyUrl,
            userAgent = userAgent,
        )
        val error = speedTestThrowable
        if (error != null) {
            return kotlinx.coroutines.flow.flow { throw error }
        }
        return kotlinx.coroutines.flow.flow {
            for (response in speedTestResponses) {
                emit(response)
            }
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

    override suspend fun takeOverService() {
        takeOverServiceThrowable?.let { throw it }
        takeOverServiceCalls += 1
    }
    override suspend fun startService(request: StartServiceRequest) = Unit
    override suspend fun stopService() = Unit
    override suspend fun getClientMetadata(): GetClientMetadataResponse =
        GetClientMetadataResponse.getDefaultInstance()

    override suspend fun setStartAtBoot(enabled: Boolean) = Unit

    override suspend fun probe() {
        probeCalls += 1
        probeThrowable?.let { throw it }
    }

    override suspend fun close() {
        closed = true
    }
}
