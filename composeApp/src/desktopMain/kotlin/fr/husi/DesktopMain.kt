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
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
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
import fr.husi.cli.ApiCommand
import fr.husi.cli.libcoreLoadFailureMessage
import fr.husi.compose.theme.AppTheme
import fr.husi.database.DataStore
import fr.husi.di.initHusiKoin
import fr.husi.ktx.Logs
import fr.husi.ktx.exitApplication
import fr.husi.ktx.invariantDirectoryPathString
import fr.husi.ktx.sha256Hex
import fr.husi.libcore.Libcore
import fr.husi.libcore.loadCA
import fr.husi.platform.PlatformInfo
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
import fr.husi.ui.MainScreen
import fr.husi.utils.CrashHandler
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JOptionPane
import javax.swing.JTextArea
import javax.swing.UIManager
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import dev.nucleusframework.composenativetray.menu.api.Key as TrayKey

/** Well-known UDS name under the core host dir (mirrors coresvc.Socket). */
const val CORE_SOCKET_NAME = "api.sock"

const val APP_NAME = "fr.husi"

/** anja loads the JNI library from this directory when set (no jar-embedded fallback). */
private const val ANJA_NATIVES_DIR_PROPERTY = "anja.natives.dir"

fun main(args: Array<String>) {
    configureNucleusAppIdentity()
    // Before any Libcore class load: packaged installs point at the sidecar library.
    configureAnjaNativesDir()
    DesktopMain(args).main(args)
    // Go threads that delivered a callback stay attached to the JVM as non-daemon
    // threads, so a command that consumed a stream would otherwise never return.
    exitProcess(0)
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

class DesktopMain(
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
            OpenCommand(),
            ApiCommand(),
        )
    }

    /**
     * Base path of the per-user session host; [CORE_SOCKET_NAME] lives directly
     * under it. A system daemon listens elsewhere, so callers dial through
     * [connectExistingHost] rather than this path alone.
     */
    val socketBasePath: String
        get() = createDesktopRepository().sessionSocketBasePath

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
        SingleInstanceManager.configuration = singleInstanceConfiguration(repository)
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

        val configuration = singleInstanceConfiguration(repository)
        if (!many && forwardTaskToRunningInstance(configuration, taskId)) {
            return 0
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

private const val LOCK_ID_HASH_LENGTH = 16

/**
 * Single-instance lock and restore-request file names for this data directory. The lock
 * identifier hashes the files directory so instances started with different `-d` data
 * directories coexist.
 */
private fun singleInstanceConfiguration(
    repository: DesktopRepository,
): SingleInstanceManager.Configuration {
    val filesDir = repository.filesDir.invariantDirectoryPathString()
    return SingleInstanceManager.Configuration(
        lockIdentifier = "$APP_NAME-${filesDir.sha256Hex().take(LOCK_ID_HASH_LENGTH)}",
    )
}

/**
 * Hands a scheduled task to the running app, which owns the same database and settings
 * this task would otherwise touch from a second process.
 *
 * The core host is the wrong messenger for this: a session host has no UI attached, and a
 * system daemon is shared between logins, so neither can reach the app process. The
 * single-instance lock can — it is held by the app process itself — so a task travels the
 * same restore-request channel as a deep link.
 *
 * @return true when the running app took the task over.
 */
private fun forwardTaskToRunningInstance(
    configuration: SingleInstanceManager.Configuration,
    taskId: String,
): Boolean {
    return runningInstanceHoldsLock(configuration) && sendTaskRequest(configuration, taskId)
}

/**
 * Probes the single-instance lock without keeping it: a task run that held the lock would
 * make a GUI launch during that run mistake this process for the running app and exit
 * silently.
 */
internal fun runningInstanceHoldsLock(
    configuration: SingleInstanceManager.Configuration,
): Boolean {
    val lockFile = configuration.lockFilePath.toFile()
    if (!lockFile.isFile) return false
    return try {
        RandomAccessFile(lockFile, "rw").use { file ->
            val lock = file.channel.tryLock()
            // Acquiring it means nobody was there to hold it.
            lock?.release()
            lock == null
        }
    } catch (e: Exception) {
        Logs.w("probe single-instance lock", e)
        false
    }
}

/** Writes a [RESTORE_PAYLOAD_TASK] request for the primary instance to pick up. */
internal fun sendTaskRequest(
    configuration: SingleInstanceManager.Configuration,
    taskId: String,
): Boolean {
    return try {
        val payload = Files.createTempFile(configuration.lockIdentifier, ".restore_request")
        Files.write(payload, listOf(RESTORE_PAYLOAD_TASK, taskId))
        // Nucleus watches for a created file, so the payload has to appear complete.
        Files.move(
            payload,
            configuration.restoreRequestFilePath,
            StandardCopyOption.REPLACE_EXISTING,
        )
        true
    } catch (e: Exception) {
        Logs.w("forward task $taskId to the running instance", e)
        false
    }
}

/**
 * First line of a single-instance restore-request payload, telling the primary instance
 * what the remaining lines are and what to do with its window.
 *
 * [RESTORE_PAYLOAD_RESTORE] and [RESTORE_PAYLOAD_SILENT] carry deep links to import, the
 * latter keeping the window untouched (login auto-start racing an already running
 * instance). [RESTORE_PAYLOAD_TASK] carries scheduled task ids and never touches the
 * window: nobody asked for it, a timer did.
 */
private const val RESTORE_PAYLOAD_RESTORE = "restore"
private const val RESTORE_PAYLOAD_SILENT = "silent"
internal const val RESTORE_PAYLOAD_TASK = "task"

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
    val payload = lines.drop(1)
    if (lines.firstOrNull() == RESTORE_PAYLOAD_TASK) {
        // Dispatch is asynchronous; a task must not stall the watcher thread.
        for (taskId in payload) {
            DesktopTaskRegistry.dispatch(taskId)
        }
        return
    }
    for (link in payload) {
        DeepLinkDispatcher.emit(link)
    }
    if (lines.firstOrNull() != RESTORE_PAYLOAD_SILENT) {
        InstanceRestoreBus.fire()
    }
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

internal val CLI_STREAM_TIMEOUT = 5.seconds

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
