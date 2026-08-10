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
import androidx.compose.ui.window.application
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
import com.kdroid.composetray.menu.api.KeyShortcut
import com.kdroid.composetray.tray.api.Tray
import fr.husi.bg.BackendState
import fr.husi.bg.DeepLinkDispatcher
import fr.husi.bg.DesktopNotificationCenter
import fr.husi.bg.DesktopTaskRegistry
import fr.husi.bg.DesktopTaskScheduler
import fr.husi.bg.RouteAssetUpdater
import fr.husi.bg.ServiceState
import fr.husi.bg.SubscriptionUpdater
import fr.husi.compose.theme.AppTheme
import fr.husi.database.DataStore
import fr.husi.di.initHusiKoin
import fr.husi.ktx.Logs
import fr.husi.ktx.exitApplication
import fr.husi.ktx.invariantDirectoryPathString
import fr.husi.ktx.toList
import fr.husi.ktx.toStringIterator
import fr.husi.libcore.Client
import fr.husi.libcore.Libcore
import fr.husi.libcore.loadCA
import fr.husi.platform.PlatformInfo
import fr.husi.repository.DesktopRepository
import fr.husi.repository.resolveDesktopRepository
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.resources.close
import fr.husi.resources.exit
import fr.husi.resources.ic_service_active
import fr.husi.resources.instance_already_running
import fr.husi.resources.instance_already_running_title
import fr.husi.resources.service_mode
import fr.husi.resources.service_mode_proxy
import fr.husi.resources.service_mode_vpn
import fr.husi.resources.start
import fr.husi.resources.stop
import fr.husi.ui.MainScreen
import fr.husi.ui.LogLevel
import fr.husi.utils.CrashHandler
import fr.husi.utils.closeQuietly
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.concurrent.TimeUnit
import javax.swing.JOptionPane
import javax.swing.JTextArea
import javax.swing.UIManager
import kotlin.system.exitProcess
import com.kdroid.composetray.menu.api.Key as TrayKey

private const val APP_NAME = "fr.husi"

fun main(args: Array<String>) {
    configureNucleusAppIdentity()
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

private class DesktopMain(
    private val rawArgs: Array<String>,
) : CliktCommand(APP_NAME) {

    companion object {
        private const val MIN_LOG_LEVEL = 0
        private const val MAX_LOG_LEVEL = 6

        private const val PREFERENCE_NODE_PROPERTY_NAME = "me.zhanghai.compose.preference.node"
        private const val PREFERENCE_NODE_NAME = "/fr/husi/preference"
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

    /** Base path the running instance listens on; [Libcore.Socket] lives directly under it. */
    val socketBasePath: String
        get() = createDesktopRepository().filesDir.invariantDirectoryPathString()

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

        application {
            val repository = resolveDesktopRepository()
            val supportTray = remember { isNativeTrayLikelySupported() }
            val startInBackground = background || launchedAtLogin
            var windowVisible by remember {
                mutableStateOf(!startInBackground || !supportTray)
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

            fun exitGracefully() {
                runCatching {
                    runBlocking {
                        repository.stopService()
                    }
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

                if (supportTray) {
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
                }

                Window(
                    onCloseRequest = {
                        if (supportTray) {
                            windowVisible = false
                        } else {
                            exitGracefully()
                        }
                    },
                    state = windowState,
                    visible = windowVisible,
                    title = appName,
                    icon = iconServiceActive,
                ) {
                    AppTheme {
                        MainScreen(
                            moveToBackground = {
                                if (supportTray) {
                                    windowVisible = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun isNativeTrayLikelySupported(): Boolean {
        if (!PlatformInfo.isLinux) {
            return true
        }
        return isStatusNotifierWatcherAvailable()
    }

    private fun isStatusNotifierWatcherAvailable(): Boolean {
        return runCatching {
            val process = ProcessBuilder("busctl", "--user", "--no-pager", "--no-legend", "list")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exited = process.waitFor(500, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
            }
            exited && process.exitValue() == 0 && output.contains("org.kde.StatusNotifierWatcher")
        }.getOrDefault(false)
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
        val filesDir = repository.filesDir.invariantDirectoryPathString()

        if (!many) {
            when (val result = checkExistingInstance(filesDir, deepLinks)) {
                ExistingInstanceCheckResult.NotFound -> Unit

                is ExistingInstanceCheckResult.LibcoreJNIBroken -> {
                    warnLibcoreLoadFailureAndExit(result.e)
                }

                ExistingInstanceCheckResult.ExistsNoDeepLink
                    if (launchedAtLogin) -> exitApplication()

                ExistingInstanceCheckResult.ExistsNoDeepLink,
                ExistingInstanceCheckResult.ExistsForwardFailed,
                    -> warnForExistInstanceAndExit(repository, filesDir)

                ExistingInstanceCheckResult.ExistsForwarded -> exitApplication()
            }
        }

        bootstrapDesktopRuntime(repository, startCommandServer = true)
    }

    /**
     * @return Exit code
     */
    private fun runTaskMode(taskId: String): Int {
        DesktopTaskRegistry.require(taskId)
        val repository = createDesktopRepository()
        val filesDir = repository.filesDir.invariantDirectoryPathString()

        when (checkExistingTaskInstance(filesDir, taskId)) {
            ExistingTaskDispatchResult.NotFound -> Unit
            ExistingTaskDispatchResult.Forwarded -> return 0
            ExistingTaskDispatchResult.ForwardFailed -> return 1
        }

        bootstrapDesktopRuntime(repository, startCommandServer = false)
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
        startCommandServer: Boolean,
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
        if (startCommandServer) {
            repository.boxService?.start()
        }
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

private sealed interface ExistingInstanceCheckResult {
    object NotFound : ExistingInstanceCheckResult
    class LibcoreJNIBroken(val e: LinkageError) : ExistingInstanceCheckResult
    object ExistsNoDeepLink : ExistingInstanceCheckResult
    object ExistsForwarded : ExistingInstanceCheckResult
    object ExistsForwardFailed : ExistingInstanceCheckResult
}

private enum class ExistingTaskDispatchResult {
    NotFound,
    Forwarded,
    ForwardFailed,
}

private fun checkExistingInstance(
    socketBasePath: String,
    deepLinks: List<String>,
): ExistingInstanceCheckResult {
    val client = try {
        connectExistingClient(socketBasePath)
    } catch (e: LinkageError) {
        return ExistingInstanceCheckResult.LibcoreJNIBroken(e)
    } catch (_: Exception) {
        null
    } ?: return ExistingInstanceCheckResult.NotFound
    return try {
        if (deepLinks.isEmpty()) {
            ExistingInstanceCheckResult.ExistsNoDeepLink
        } else if (forwardDeepLinks(client, deepLinks)) {
            ExistingInstanceCheckResult.ExistsForwarded
        } else {
            ExistingInstanceCheckResult.ExistsForwardFailed
        }
    } finally {
        client.close()
    }
}

private fun connectExistingClient(socketBasePath: String): Client? {
    val client = Libcore.newClient(socketBasePath)
    runCatching {
        client.hello()
    }.onFailure {
        Logs.w("probe existing desktop instance", it)
        runCatching {
            client.close()
        }
        return null
    }
    return client
}

private fun forwardDeepLinks(client: Client, deepLinks: List<String>): Boolean {
    return runCatching {
        client.importDeepLinks(deepLinks.toStringIterator(deepLinks.size))
    }.onFailure {
        Logs.e(it)
    }.isSuccess
}

private fun checkExistingTaskInstance(
    socketBasePath: String,
    taskId: String,
): ExistingTaskDispatchResult {
    val client = connectExistingClient(socketBasePath) ?: return ExistingTaskDispatchResult.NotFound
    return try {
        if (forwardTask(client, taskId)) {
            ExistingTaskDispatchResult.Forwarded
        } else {
            ExistingTaskDispatchResult.ForwardFailed
        }
    } finally {
        client.close()
    }
}

private fun forwardTask(client: Client, taskId: String): Boolean {
    return runCatching {
        client.runTask(taskId)
    }.onFailure {
        Logs.e(it)
    }.isSuccess
}

private fun warnForExistInstanceAndExit(repository: DesktopRepository, socketBasePath: String) {
    val socketPath = socketBasePath + Libcore.Socket
    val title = runBlocking { repository.getString(Res.string.instance_already_running_title) }
    val message = runBlocking {
        repository.getString(Res.string.instance_already_running, socketPath)
    }
    try {
        showSelectableMessageDialog(message, title, JOptionPane.WARNING_MESSAGE)
    } catch (e: Exception) {
        System.err.println("$title: $message")
        System.err.println(e.message)
    }
    exitProcess(1)
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

/**
 * Reads the current clash mode over a dedicated connection.
 *
 * There is no one-shot "get mode" command, but [Client.subscribeClashMode] emits the current mode
 * first. We read that first emission on a daemon thread, then close the socket to unblock the Go
 * read loop. A separate connection is used so the streaming read never interleaves with one-shot
 * queries on the caller's client.
 *
 * @return the current mode, or null if no instance is reachable or it did not emit in time.
 */
private fun currentClashMode(socketBasePath: String): String? {
    val client = try {
        connectExistingClient(socketBasePath)
    } catch (_: Throwable) {
        null
    } ?: return null
    return runBlocking {
        val firstMode = CompletableDeferred<String>()
        val reader = launch(Dispatchers.IO) {
            runCatching {
                // Blocks until the socket closes; the first emission is the current mode.
                client.subscribeClashMode { mode -> firstMode.complete(mode) }
            }
        }
        try {
            withTimeoutOrNull(2_000) { firstMode.await() }
        } finally {
            client.closeQuietly() // unblock the native read so `reader` can finish
            reader.cancel()
        }
    }
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

    protected fun <T> withRunningClient(block: (Client) -> T): T {
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
            echo("No running $APP_NAME instance (socket: $base/${Libcore.Socket}).", err = true)
            throw ProgramResult(1)
        }
        return try {
            block(client)
        } finally {
            client.closeQuietly()
        }
    }
}

private class StatusCommand : ClientCommand("status") {
    override fun run() = withRunningClient { client ->
        val memory = client.queryMemory()
        val goroutines = client.queryGoroutines()
        val connections = client.queryConnections().toList()
        val active = connections.count { it.closedAt.isEmpty() }
        val closed = connections.size - active
        val modes = client.queryClashModes().toList()
        val current = currentClashMode(root.socketBasePath)
        if (json) {
            echoJson(
                buildJsonObject {
                    put("running", true)
                    put("memory", memory)
                    put("memoryReadable", Libcore.formatMemoryBytes(memory))
                    put("goroutines", goroutines)
                    putJsonObject("connections") {
                        put("active", active)
                        put("closed", closed)
                        put("total", connections.size)
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
                appendLine("memory:      $memory (${Libcore.formatMemoryBytes(memory)})")
                appendLine("goroutines:  $goroutines")
                appendLine("connections: $active active, $closed closed")
                append("clash mode:  ${current ?: "unknown"}")
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
        val modes = client.queryClashModes().toList()
        val target = mode
        if (target == null) {
            val current = currentClashMode(root.socketBasePath)
            if (json) {
                echoJson(
                    buildJsonObject {
                        put("current", current)
                        putJsonArray("available") { for (entry in modes) add(entry) }
                    },
                )
                return@withRunningClient
            }
            echo("current:   ${current ?: "unknown"}")
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
            val filtered = client.queryConnections().toList().filter { info ->
                val isClosed = info.closedAt.isNotEmpty()
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
                                addJsonObject {
                                    put("uuid", info.uuid)
                                    put("state", if (info.closedAt.isNotEmpty()) "closed" else "active")
                                    put("network", info.network)
                                    put("src", info.src)
                                    put("dst", info.dst)
                                    put("host", info.host)
                                    put("outbound", info.outbound)
                                    put("rule", info.matchedRule)
                                    put("protocol", info.protocol)
                                    put("chain", info.chain)
                                    put("uploadTotal", info.uploadTotal)
                                    put("downloadTotal", info.downloadTotal)
                                    put("startedAt", info.startedAt)
                                    put("closedAt", info.closedAt)
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
                val state = if (info.closedAt.isNotEmpty()) "closed" else "active"
                echo(
                    "%s  %-6s  %-5s  %s -> %s  host=%s  up %s  down %s%s".format(
                        info.uuid,
                        state,
                        info.network,
                        info.src,
                        info.dst,
                        info.host.ifEmpty { "-" },
                        Libcore.formatBytes(info.uploadTotal),
                        Libcore.formatBytes(info.downloadTotal),
                        if (info.chain.isEmpty()) "" else "  [${info.chain}]",
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
            client.clearLog()
            if (json) {
                echoJson(buildJsonObject { put("ok", true) })
                return@withRunningClient
            }
            echo("log cleared")
            return@withRunningClient
        }
        // subscribeLogs replays the buffer, then streams live entries until the socket closes
        // (e.g. Ctrl-C). In JSON mode each entry is one compact object per line (JSON Lines).
        client.subscribeLogs { item ->
            val level = LogLevel.entries.getOrNull(item.level)?.name ?: item.level.toString()
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
        val memory = client.queryMemory()
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
        val goroutines = client.queryGoroutines()
        if (json) {
            echoJson(buildJsonObject { put("goroutines", goroutines) })
            return@withRunningClient
        }
        echo(goroutines.toString())
    }
}

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
