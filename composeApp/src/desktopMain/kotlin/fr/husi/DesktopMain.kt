package fr.husi

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.composenativetray.menu.api.KeyShortcut
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.core.runtime.SingleInstanceManager
import fr.husi.bg.BackendState
import fr.husi.bg.DeepLinkDispatcher
import fr.husi.bg.DesktopNotificationCenter
import fr.husi.bg.DesktopTaskRegistry
import fr.husi.bg.DesktopTaskScheduler
import fr.husi.bg.InstanceRestoreBus
import fr.husi.bg.RouteAssetUpdater
import fr.husi.bg.ServiceState
import fr.husi.bg.SubscriptionUpdater
import fr.husi.compose.theme.AppTheme
import fr.husi.core.BridgeCoreClient
import fr.husi.core.CoreClient
import fr.husi.core.chainLabel
import fr.husi.core.formatConnectionTime
import fr.husi.core.inboundLabel
import fr.husi.core.matchedRuleOrFinal
import fr.husi.core.outboundLabel
import fr.husi.database.DataStore
import fr.husi.di.initHusiKoin
import fr.husi.ktx.Logs
import fr.husi.ktx.exitApplication
import fr.husi.ktx.invariantDirectoryPathString
import fr.husi.ktx.sha256Hex
import fr.husi.libcore.Libcore
import fr.husi.libcore.loadCA
import fr.husi.platform.PlatformInfo
import fr.husi.proto.v1.Hosting
import fr.husi.repository.DesktopRepository
import fr.husi.repository.resolveDesktopRepository
import fr.husi.repository.resolvePackagedAnjaNativesDir
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.resources.close
import fr.husi.resources.exit
import fr.husi.resources.ic_service_active
import fr.husi.resources.service_mode
import fr.husi.resources.service_mode_proxy
import fr.husi.resources.service_mode_vpn
import fr.husi.resources.start
import fr.husi.resources.stop
import fr.husi.ui.LogLevel
import fr.husi.ui.MainScreen
import fr.husi.utils.CrashHandler
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JOptionPane
import javax.swing.JTextArea
import javax.swing.UIManager
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import dev.nucleusframework.composenativetray.menu.api.Key as TrayKey

/** Well-known UDS name under the core host dir (mirrors coresvc.Socket). */
private const val CORE_SOCKET_NAME = "api.sock"

private const val APP_NAME = "fr.husi"

/** anja loads the JNI library from this directory when set (no jar-embedded fallback). */
private const val ANJA_NATIVES_DIR_PROPERTY = "anja.natives.dir"

fun main(args: Array<String>) {
    configureNucleusAppIdentity()
    // Before any Libcore class load: packaged installs point at the sidecar library.
    configureAnjaNativesDir()
    DesktopMain(args).main(args)
}

/**
 * Nucleus runtime modules resolve the app identity from these properties (normally injected by
 * the Nucleus Gradle plugin, which we do not use). NucleusApp caches them on first access, so
 * they must be set before any Nucleus API is touched: the Windows toast backend derives its
 * AUMID and Start Menu shortcut name from them, and AutoLaunch login-launch detection — which
 * runs ahead of the runtime bootstrap — keys its systemd unit name on the app id.
 */
private fun configureNucleusAppIdentity() {
    System.setProperty("nucleus.app.id", APP_NAME)
    System.setProperty("nucleus.app.name", "Husi")
}

/**
 * N4: if unset, probe the packaged layout for the anja library next to the launcher /
 * husi-core and set `anja.natives.dir`. Found nothing → leave unset so the fat jar's
 * embedded copy is used (dev / `gradlew run`).
 */
private fun configureAnjaNativesDir() {
    if (!System.getProperty(ANJA_NATIVES_DIR_PROPERTY).isNullOrEmpty()) {
        return
    }
    val nativesDir = resolvePackagedAnjaNativesDir() ?: return
    System.setProperty(ANJA_NATIVES_DIR_PROPERTY, nativesDir.absolutePath)
}

private class DesktopMain(
    private val rawArgs: Array<String>,
) : CliktCommand(APP_NAME) {

    companion object {
        private const val MIN_LOG_LEVEL = 0
        private const val MAX_LOG_LEVEL = 6

        private const val PREFERENCE_NODE_PROPERTY_NAME = "me.zhanghai.compose.preference.node"
        private const val PREFERENCE_NODE_NAME = "/fr/husi/preference"

        private const val LOCK_ID_HASH_LENGTH = 16
    }

    val baseDir: File? by option(
        "-d",
        "--dir",
        help = "Data directory",
    ).file(
        canBeFile = false,
        canBeDir = true,
        mustBeWritable = true,
        mustBeReadable = true,
    )

    val logLevel: Int? by option(
        "-l",
        "--log-level",
        help = "Log level override (0-6)",
    ).int().restrictTo(MIN_LOG_LEVEL..MAX_LOG_LEVEL)

    val many: Boolean by option(
        "-m",
        "--many",
        help = "Ignore exist instance",
    ).flag()

    val autoStart: Boolean by option(
        "--autostart",
        hidden = true,
        help = "[Internal] Started by system autostart. This option should only be added by program itself, not by users.",
    ).flag()

    val background: Boolean by option(
        "-b",
        "--background",
        help = "Start without opening the main window",
    ).flag()

    val taskId: String? by option(
        "--task",
        hidden = true,
        help = "[Internal] Run a hidden desktop task and exit.",
    )

    /**
     * True when this process was launched by the login auto-start mechanism: via the explicit
     * flags (Windows Run entry, legacy entries) or via platform detection for the argument-less
     * Linux systemd unit / macOS SMAppService registrations.
     */
    private val launchedAtLogin: Boolean by lazy {
        autoStart || DesktopAutoStart.wasStartedAtLogin(rawArgs)
    }

    override val invokeWithoutSubcommand = true

    init {
        subcommands(
            StatusCommand(),
            ModeCommand(),
            ConnCommand(),
            LogCommand(),
            ResetNetworkCommand(),
            MemoryCommand(),
            GoroutinesCommand(),
            OpenCommand(),
        )
    }

    /** Base path the core host listens on; [CORE_SOCKET_NAME] lives directly under it. */
    val socketBasePath: String
        get() = createDesktopRepository().coreSocketBasePath

    override fun run() {
        currentContext.obj = this
        // Subcommands handle themselves; only the no-subcommand invocation launches the GUI.
        // Deep links arrive through the `open` subcommand (see OpenCommand / husi.desktop Exec).
        if (currentContext.invokedSubcommand != null) return
        launchGui(emptyList())
    }

    fun launchGui(deepLinks: List<String>) {
        taskId?.let {
            exitProcess(runTaskMode(it))
        }

        registerMacOSOpenUriHandler()
        initDesktopRuntime(deepLinks)
        runCatching {
            runBlocking {
                SubscriptionUpdater.reconfigureUpdater()
                RouteAssetUpdater.reconfigureUpdater()
            }
        }.onFailure {
            Logs.e("reconfigure desktop tasks on startup", it)
        }
        for (link in deepLinks) {
            DeepLinkDispatcher.emit(link)
        }

        // AWT backend explicitly: the tray library links against the Tao backend, and Auto
        // resolution would pick Tao if it ever lands on the classpath — husi's windows are
        // plain Compose/AWT. Nucleus's built-in single instance stays off: husi drives
        // SingleInstanceManager itself in initDesktopRuntime, before libcore bootstrap and
        // with a payload carrying multiple deep links (the built-in path handles one URI
        // and unconditionally restores the window).
        nucleusApplication(
            args = rawArgs,
            backend = NucleusBackend.Awt,
            enableSingleInstance = false,
        ) {
            val repository = resolveDesktopRepository()
            val startInBackground = background || launchedAtLogin
            var windowVisible by remember {
                mutableStateOf(!startInBackground)
            }

            val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))

            fun openWindow() {
                windowVisible = true
                windowState.isMinimized = false
            }

            LaunchedEffect(Unit) {
                DesktopNotificationCenter.activations.collect {
                    openWindow()
                }
            }

            LaunchedEffect(Unit) {
                InstanceRestoreBus.restores.collect {
                    openWindow()
                }
            }

            fun exitGracefully() {
                runCatching {
                    repository.coreHostController.shutdownHost()
                }
                exitApplication()
            }

            DesktopResourceEnvironmentFix {
                LaunchedEffect(Unit) {
                    if (shouldAutoConnectOnLaunch()) {
                        repository.startService()
                    }
                }

                val appName = stringResource(Res.string.app_name)
                val iconServiceActive = painterResource(Res.drawable.ic_service_active)

                val serviceStatus by BackendState.status.collectAsState()
                val switchText = stringResource(
                    if (serviceStatus.state == ServiceState.Connected) {
                        Res.string.stop
                    } else {
                        Res.string.start
                    },
                )

                val textServiceMode = stringResource(Res.string.service_mode)
                val textServiceModeProxy = stringResource(Res.string.service_mode_proxy)
                val textServiceModeVpn = stringResource(Res.string.service_mode_vpn)
                val serviceMode by DataStore.configurationStore
                    .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
                    .collectAsState(Key.MODE_VPN)

                val textExit = stringResource(Res.string.exit)
                val iconClose = painterResource(Res.drawable.close)
                Tray(
                    icon = iconServiceActive,
                    tooltip = appName,
                    primaryAction = ::openWindow,
                    menuContent = {
                        Item(
                            label = serviceStatus.profileName ?: appName,
                            shortcut = KeyShortcut(TrayKey.O),
                        ) {
                            openWindow()
                        }
                        CheckableItem(
                            label = switchText,
                            checked = serviceStatus.state == ServiceState.Connected
                                    || serviceStatus.state == ServiceState.Stopped
                                    || serviceStatus.state == ServiceState.Idle,
                            onCheckedChange = {
                                when (serviceStatus.state) {
                                    ServiceState.Stopped -> repository.startService()
                                    ServiceState.Idle, ServiceState.Connected -> repository.stopService()
                                    else -> {}
                                }
                            },
                            shortcut = KeyShortcut(TrayKey.Return, ctrl = true),
                        )
                        SubMenu(
                            label = textServiceMode,
                        ) {
                            CheckableItem(
                                label = textServiceModeProxy,
                                checked = serviceMode == Key.MODE_PROXY,
                                onCheckedChange = {
                                    if (serviceMode != Key.MODE_PROXY) {
                                        DataStore.serviceMode = Key.MODE_PROXY
                                        repository.reloadService()
                                    }
                                },
                            )
                            CheckableItem(
                                label = textServiceModeVpn,
                                checked = serviceMode == Key.MODE_VPN,
                                onCheckedChange = {
                                    if (serviceMode != Key.MODE_VPN) {
                                        DataStore.serviceMode = Key.MODE_VPN
                                        repository.reloadService()
                                    }
                                },
                            )
                        }
                        Item(
                            label = textExit,
                            icon = iconClose,
                            shortcut = KeyShortcut(TrayKey.Q),
                            onClick = ::exitGracefully,
                        )
                    },
                )

                Window(
                    onCloseRequest = { windowVisible = false },
                    state = windowState,
                    visible = windowVisible,
                    title = appName,
                    icon = iconServiceActive,
                ) {
                    AppTheme {
                        MainScreen(
                            moveToBackground = {
                                windowVisible = false
                            },
                        )
                    }
                }
            }
        }
    }

    private fun shouldAutoConnectOnLaunch(): Boolean {
        return launchedAtLogin
                && DataStore.persistAcrossReboot
                && DataStore.selectedProxy > 0L
                && !DataStore.serviceState.started
    }

    private fun initDesktopRuntime(deepLinks: List<String>) {
        fixComposePreferenceNode()
        val repository = createDesktopRepository()

        if (!many && !acquireSingleInstanceLock(repository, deepLinks)) {
            // A running instance holds the lock and has been handed this launch's payload.
            exitApplication()
        }

        bootstrapDesktopRuntime(repository, startCoreHost = true)
    }

    /**
     * Acquires the single-instance file lock via Nucleus.
     *
     * The lock identifier hashes the files directory so instances started with different
     * `-d` data directories coexist, matching the per-directory scoping of the command
     * socket. Lock files stay in the default temp directory: Nucleus writes the
     * restore-request payload to a temp file and moves it into place, and keeping both on
     * the same filesystem makes that move an atomic rename.
     *
     * On the primary instance this registers a watcher handling later launches' payloads;
     * on a secondary launch it writes the payload for the primary and returns false.
     */
    private fun acquireSingleInstanceLock(
        repository: DesktopRepository,
        deepLinks: List<String>,
    ): Boolean {
        val filesDir = repository.filesDir.invariantDirectoryPathString()
        SingleInstanceManager.configuration = SingleInstanceManager.Configuration(
            lockIdentifier = "$APP_NAME-${filesDir.sha256Hex().take(LOCK_ID_HASH_LENGTH)}",
        )
        // A login auto-start finding an instance already running should stay unnoticed;
        // any other secondary launch pops the primary's window up.
        val restoreWindow = deepLinks.isNotEmpty() || !launchedAtLogin
        return SingleInstanceManager.isSingleInstance(
            onRestoreFileCreated = {
                writeRestorePayload(this, restoreWindow, deepLinks)
            },
            onRestoreRequest = {
                handleRestoreRequest(this)
            },
        )
    }

    /**
     * @return Exit code
     */
    private fun runTaskMode(taskId: String): Int {
        DesktopTaskRegistry.require(taskId)
        val repository = createDesktopRepository()
        val socketBase = repository.coreSocketBasePath

        when (checkExistingTaskInstance(socketBase, taskId)) {
            ExistingTaskDispatchResult.NotFound -> Unit
            ExistingTaskDispatchResult.Forwarded -> return 0
            ExistingTaskDispatchResult.ForwardFailed -> return 1
        }

        // Task-only processes do not spawn a core session host.
        bootstrapDesktopRuntime(repository, startCoreHost = false)
        return try {
            runBlocking {
                DesktopTaskRegistry.require(taskId).run()
            }
            0
        } catch (e: Exception) {
            Logs.e("run desktop task $taskId", e)
            1
        }
    }

    private fun fixComposePreferenceNode() {
        System.setProperty(PREFERENCE_NODE_PROPERTY_NAME, PREFERENCE_NODE_NAME)
    }

    private fun createDesktopRepository(): DesktopRepository {
        val baseDir = baseDir ?: DesktopPaths.dataDir
        baseDir.mkdirs()
        return DesktopRepository(baseDir)
    }

    private fun bootstrapDesktopRuntime(
        repository: DesktopRepository,
        startCoreHost: Boolean,
    ) {
        DesktopNotificationCenter.initialize()
        DesktopTaskScheduler.initialize()
        initHusiKoin(repository)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        val cacheDir = repository.cacheDir.invariantDirectoryPathString()
        val filesDir = repository.filesDir.invariantDirectoryPathString()
        val externalAssetsDir = repository.externalAssetsDir.invariantDirectoryPathString()

        val rulesProvider = DataStore.rulesProvider
        val isOfficialProvider = rulesProvider == RuleProvider.OFFICIAL
        if (isOfficialProvider) {
            runBlocking {
                copyBundledRuleSetAssetsIfNeeded()
            }
        }
        try {
            // First touch of the Libcore class in this process: loads the JNI library.
            // Desktop still needs libcore for link parsing, formats, and the gRPC bridge client.
            Libcore.initCore(
                true,
                true,
                cacheDir,
                filesDir,
                externalAssetsDir,
                DataStore.logMaxLine,
                logLevel ?: DataStore.logLevel,
                isOfficialProvider,
                DataStore.isExpert,
            )
            loadCA(DataStore.certProvider)
        } catch (e: LinkageError) {
            warnLibcoreLoadFailureAndExit(e)
        }
        if (startCoreHost) {
            try {
                repository.coreHostController.ensureHost()
            } catch (e: Exception) {
                Logs.e("failed to start core host session", e)
                warnCoreHostFailureAndExit(e)
            }
        }
    }
}

private fun warnCoreHostFailureAndExit(error: Exception): Nothing {
    val title = "Failed to start core host"
    val message = buildString {
        appendLine("Husi could not start the out-of-process core host (husi-core).")
        appendLine()
        appendLine("Build it with: make core_desktop DESKTOP_TARGETS=host")
        appendLine("or install a package that bundles husi-core next to the launcher.")
        appendLine()
        appendLine("Error: ${error.message ?: error::class.simpleName}")
    }.trimEnd()
    System.err.println("$title: $message")
    System.err.println(error.stackTraceToString())
    try {
        showSelectableMessageDialog(message, title, JOptionPane.ERROR_MESSAGE)
    } catch (dialogError: Exception) {
        System.err.println(dialogError.message)
    }
    exitProcess(1)
}

/**
 * First line of a single-instance restore-request payload; the remaining lines are deep
 * links to import. [RESTORE_PAYLOAD_SILENT] keeps the primary's window untouched (login
 * auto-start racing an already running instance), anything else brings it to the front.
 */
private const val RESTORE_PAYLOAD_RESTORE = "restore"
private const val RESTORE_PAYLOAD_SILENT = "silent"

private fun writeRestorePayload(path: Path, restoreWindow: Boolean, deepLinks: List<String>) {
    val lines = buildList {
        add(if (restoreWindow) RESTORE_PAYLOAD_RESTORE else RESTORE_PAYLOAD_SILENT)
        addAll(deepLinks)
    }
    Files.write(path, lines)
}

/** Runs on the Nucleus watcher thread of the primary instance. */
private fun handleRestoreRequest(path: Path) {
    val lines = try {
        Files.readAllLines(path)
    } catch (e: Exception) {
        Logs.w("read single-instance restore request", e)
        return
    }
    for (link in lines.drop(1)) {
        DeepLinkDispatcher.emit(link)
    }
    if (lines.firstOrNull() != RESTORE_PAYLOAD_SILENT) {
        InstanceRestoreBus.fire()
    }
}

private fun libcoreLoadFailureMessage(error: LinkageError): String {
    return buildString {
        appendLine("Husi could not load the libcore JNI library.")
        appendLine()
        appendLine("This usually means the desktop libcore package does not match this system,")
        appendLine("or developer made mistakes.")
        appendLine()
        appendLine("System: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        appendLine("Java: ${System.getProperty("java.version")}")
        appendLine("Error: ${error.message ?: error::class.simpleName}")
    }.trimEnd()
}

private fun warnLibcoreLoadFailureAndExit(error: LinkageError): Nothing {
    val title = "Failed to load libcore"
    val message = libcoreLoadFailureMessage(error)
    System.err.println("$title: $message")
    System.err.println(error.stackTraceToString())
    try {
        showSelectableMessageDialog(message, title, JOptionPane.ERROR_MESSAGE)
    } catch (dialogError: Exception) {
        System.err.println(dialogError.message)
    }
    exitProcess(1)
}

private fun registerMacOSOpenUriHandler() {
    if (!PlatformInfo.isMacOs) return
    try {
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return
        desktop.setOpenURIHandler { event ->
            DeepLinkDispatcher.emit(event.uri.toString())
        }
    } catch (e: Exception) {
        Logs.w("register macOS open-uri handler", e)
    }
}

private enum class ExistingTaskDispatchResult {
    NotFound,
    Forwarded,
    ForwardFailed,
}

private fun connectExistingClient(socketBasePath: String): CoreClient? {
    val client = BridgeCoreClient(socketBasePath)
    runCatching {
        runBlocking { client.probe() }
    }.onFailure {
        Logs.w("probe existing desktop instance", it)
        runCatching {
            runBlocking { client.close() }
        }
        return null
    }
    return client
}

private fun checkExistingTaskInstance(
    socketBasePath: String,
    taskId: String,
): ExistingTaskDispatchResult {
    val client = connectExistingClient(socketBasePath) ?: return ExistingTaskDispatchResult.NotFound
    return try {
        // Session mode has no UI AppHandler on the core host, so RunTask would be a
        // silent no-op. Run the task in this process instead.
        val hosting = runBlocking {
            runCatching { client.getDaemonInfo().hosting }.getOrNull()
        }
        if (hosting == Hosting.HOSTING_SESSION || hosting == null) {
            return ExistingTaskDispatchResult.NotFound
        }
        if (forwardTask(client, taskId)) {
            ExistingTaskDispatchResult.Forwarded
        } else {
            ExistingTaskDispatchResult.ForwardFailed
        }
    } finally {
        runCatching { runBlocking { client.close() } }
    }
}

private fun forwardTask(client: CoreClient, taskId: String): Boolean {
    return runCatching {
        runBlocking { client.runTask(taskId) }
    }.onFailure {
        Logs.e(it)
    }.isSuccess
}

private fun showSelectableMessageDialog(
    message: String,
    title: String,
    messageType: Int,
) {
    val columns = message.lineSequence().maxOfOrNull { it.length }
        ?.fastCoerceIn(20, 80)
        ?: 20
    val rows = message.lineSequence().count().fastCoerceIn(1, 16)
    val textArea = JTextArea(message, rows, columns).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        border = null
        font = UIManager.getFont("OptionPane.messageFont")
        foreground = UIManager.getColor("OptionPane.messageForeground")
        caretPosition = 0
    }
    JOptionPane.showMessageDialog(
        null,
        textArea,
        title,
        messageType,
    )
}

/** Pretty-printed JSON for one-shot `--json` command output. */
private val cliJson = Json { prettyPrint = true }

/** Compact JSON for newline-delimited `log --json` streaming (one object per line). */
private val cliJsonLine = Json { prettyPrint = false }

/**
 * Base for subcommands that drive a running instance over its command socket. The parent
 * [DesktopMain] publishes itself as the context object (see [DesktopMain.run]).
 */
private abstract class ClientCommand(name: String) : CliktCommand(name) {

    protected val root by requireObject<DesktopMain>()

    /**
     * When set, successful output is emitted as JSON on stdout. Errors stay human-readable on
     * stderr regardless, so a caller can rely on stdout being either valid JSON or empty.
     */
    protected val json: Boolean by option("--json", help = "Print output as JSON.").flag()

    /** Serializes [element] with [cliJson] (pretty) and prints it. */
    protected fun echoJson(element: JsonElement) {
        echo(cliJson.encodeToString(JsonElement.serializer(), element))
    }

    protected fun <T> withRunningClient(block: suspend (CoreClient) -> T): T {
        val base = root.socketBasePath
        val client = try {
            connectExistingClient(base)
        } catch (e: LinkageError) {
            echo(libcoreLoadFailureMessage(e), err = true)
            throw ProgramResult(1)
        } catch (_: Exception) {
            null
        }
        if (client == null) {
            echo("No running $APP_NAME instance (socket: $base/$CORE_SOCKET_NAME).", err = true)
            throw ProgramResult(1)
        }
        return try {
            runBlocking { block(client) }
        } finally {
            runCatching { runBlocking { client.close() } }
        }
    }
}

private class StatusCommand : ClientCommand("status") {
    override fun run() = withRunningClient { client ->
        val status = withTimeout(CLI_STREAM_TIMEOUT) {
            client.subscribeStatus(1.seconds).first()
        }
        val clash = client.getClashModeStatus()
        val modes = clash.modeListList
        val current = clash.currentMode
        if (json) {
            echoJson(
                buildJsonObject {
                    put("running", true)
                    put("memory", status.memory)
                    put("memoryReadable", Libcore.formatMemoryBytes(status.memory))
                    put("goroutines", status.goroutines)
                    putJsonObject("connections") {
                        put("in", status.connectionsIn)
                        put("out", status.connectionsOut)
                    }
                    putJsonObject("clashMode") {
                        put("current", current)
                        putJsonArray("available") { for (mode in modes) add(mode) }
                    }
                },
            )
            return@withRunningClient
        }
        echo(
            buildString {
                appendLine("running:     yes")
                appendLine("memory:      ${status.memory} (${Libcore.formatMemoryBytes(status.memory)})")
                appendLine("goroutines:  ${status.goroutines}")
                appendLine(
                    "connections: ${status.connectionsIn} in, ${status.connectionsOut} out",
                )
                append("clash mode:  ${current.ifEmpty { "unknown" }}")
                if (modes.isNotEmpty()) {
                    append(" (available: ${modes.joinToString(", ")})")
                }
            },
        )
    }
}

private class ModeCommand : ClientCommand("mode") {
    private val mode: String? by argument(
        name = "mode",
        help = "Clash mode to switch to; omit to print the current and available modes.",
    ).optional()

    override fun run() = withRunningClient { client ->
        val clash = client.getClashModeStatus()
        val modes = clash.modeListList
        val target = mode
        if (target == null) {
            val current = clash.currentMode
            if (json) {
                echoJson(
                    buildJsonObject {
                        put("current", current)
                        putJsonArray("available") { for (entry in modes) add(entry) }
                    },
                )
                return@withRunningClient
            }
            echo("current:   ${current.ifEmpty { "unknown" }}")
            echo("available: ${modes.joinToString(", ").ifEmpty { "(none)" }}")
            return@withRunningClient
        }
        if (modes.isNotEmpty() && modes.none { it.equals(target, ignoreCase = true) }) {
            echo("Unknown mode '$target'. Available: ${modes.joinToString(", ")}", err = true)
            throw ProgramResult(1)
        }
        client.setClashMode(target)
        if (json) {
            echoJson(
                buildJsonObject {
                    put("ok", true)
                    put("mode", target)
                },
            )
            return@withRunningClient
        }
        echo("clash mode set to '$target'")
    }
}

private class ConnCommand : ClientCommand("conn") {
    private val active by option("--active", help = "Show only active connections.").flag()
    private val closed by option("--closed", help = "Show only closed connections.").flag()

    override val invokeWithoutSubcommand = true

    init {
        subcommands(ConnCloseCommand())
    }

    override fun run() {
        // `conn close <uuid>` handles itself; a bare `conn` lists connections.
        if (currentContext.invokedSubcommand != null) return
        withRunningClient { client ->
            val snapshot = withTimeout(CLI_STREAM_TIMEOUT) {
                client.subscribeConnections(1.seconds).first { it.reset }
            }
            val connections = snapshot.eventsList.mapNotNull { event ->
                if (!event.hasConnection()) null else event.connection
            }
            val filtered = connections.filter { info ->
                val isClosed = info.closedAt > 0L
                when {
                    active && !closed -> !isClosed
                    closed && !active -> isClosed
                    else -> true
                }
            }
            if (json) {
                echoJson(
                    buildJsonObject {
                        putJsonArray("connections") {
                            for (info in filtered) {
                                val startedAt = formatConnectionTime(info.createdAt)
                                val closedAt = formatConnectionTime(info.closedAt)
                                val chain = info.chainLabel()
                                addJsonObject {
                                    put("uuid", info.id)
                                    put(
                                        "state",
                                        if (info.closedAt > 0L) "closed" else "active",
                                    )
                                    put("network", info.network)
                                    put("src", info.source)
                                    put("dst", info.destination)
                                    put("host", info.domain)
                                    put("outbound", info.outboundLabel())
                                    put("rule", info.matchedRuleOrFinal())
                                    put("protocol", info.protocol)
                                    put("chain", chain)
                                    put("uploadTotal", info.uplinkTotal)
                                    put("downloadTotal", info.downlinkTotal)
                                    put("startedAt", startedAt)
                                    put("closedAt", closedAt)
                                }
                            }
                        }
                        put("total", filtered.size)
                    },
                )
                return@withRunningClient
            }
            if (filtered.isEmpty()) {
                echo("no connections")
                return@withRunningClient
            }
            for (info in filtered) {
                val state = if (info.closedAt > 0L) "closed" else "active"
                val chain = info.chainLabel()
                echo(
                    "%s  %-6s  %-5s  %s -> %s  host=%s  up %s  down %s%s".format(
                        info.id,
                        state,
                        info.network,
                        info.source,
                        info.destination,
                        info.domain.ifEmpty { "-" },
                        Libcore.formatBytes(info.uplinkTotal),
                        Libcore.formatBytes(info.downlinkTotal),
                        if (chain.isEmpty()) "" else "  [$chain]",
                    ),
                )
            }
            echo("total: ${filtered.size}")
        }
    }
}

private class ConnCloseCommand : ClientCommand("close") {
    private val uuid: String by argument(
        name = "uuid",
        help = "UUID of the connection to close.",
    )

    override fun run() = withRunningClient { client ->
        client.closeConnection(uuid)
        if (json) {
            echoJson(
                buildJsonObject {
                    put("ok", true)
                    put("uuid", uuid)
                },
            )
            return@withRunningClient
        }
        echo("closed connection $uuid")
    }
}

private class LogCommand : ClientCommand("log") {
    private val clear by option("--clear", help = "Clear the log buffer, then exit.").flag()

    override fun run() = withRunningClient { client ->
        if (clear) {
            client.clearLogs()
            Libcore.logClear()
            if (json) {
                echoJson(buildJsonObject { put("ok", true) })
                return@withRunningClient
            }
            echo("log cleared")
            return@withRunningClient
        }
        // subscribeLog replays the buffer (reset batch), then streams live entries until the
        // socket closes (e.g. Ctrl-C). In JSON mode each entry is one compact object per line.
        client.subscribeLog().collect { batch ->
            for (item in batch.messagesList) {
                val level = LogLevel.entries.getOrNull(item.levelValue)?.name
                    ?: item.levelValue.toString()
                if (json) {
                    echo(
                        cliJsonLine.encodeToString(
                            JsonElement.serializer(),
                            buildJsonObject {
                                put("level", level)
                                put("message", item.message)
                            },
                        ),
                    )
                } else {
                    echo("[$level] ${item.message}")
                }
            }
        }
    }
}

private class ResetNetworkCommand : ClientCommand("reset_network") {
    override fun run() = withRunningClient { client ->
        client.resetNetwork()
        if (json) {
            echoJson(buildJsonObject { put("ok", true) })
            return@withRunningClient
        }
        echo("network reset")
    }
}

private class MemoryCommand : ClientCommand("memory") {
    override fun run() = withRunningClient { client ->
        val memory = withTimeout(CLI_STREAM_TIMEOUT) {
            client.subscribeStatus(1.seconds).first()
        }.memory
        if (json) {
            echoJson(
                buildJsonObject {
                    put("memory", memory)
                    put("memoryReadable", Libcore.formatMemoryBytes(memory))
                },
            )
            return@withRunningClient
        }
        echo("$memory (${Libcore.formatMemoryBytes(memory)})")
    }
}

private class GoroutinesCommand : ClientCommand("goroutines") {
    override fun run() = withRunningClient { client ->
        val goroutines = withTimeout(CLI_STREAM_TIMEOUT) {
            client.subscribeStatus(1.seconds).first()
        }.goroutines
        if (json) {
            echoJson(buildJsonObject { put("goroutines", goroutines) })
            return@withRunningClient
        }
        echo(goroutines.toString())
    }
}

/** One-shot CLI stream reads must not hang forever on a broken stream (FIX F9). */
private val CLI_STREAM_TIMEOUT = 5.seconds

/**
 * Imports deep links into a running instance, or launches the GUI when none are given. This is the
 * entry the desktop file / URL-scheme handler invokes (`husi open %u`), so an empty invocation must
 * behave exactly like a bare launch.
 */
private class OpenCommand : CliktCommand("open") {
    private val root by requireObject<DesktopMain>()
    private val links: List<String> by argument(
        name = "deep-link",
        help = "Deep links to import; with none given, just launches the app.",
    ).multiple()

    override fun run() {
        root.launchGui(links)
    }
}
