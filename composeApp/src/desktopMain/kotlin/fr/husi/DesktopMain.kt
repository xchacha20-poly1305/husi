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
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import fr.husi.bg.BackendState
import fr.husi.bg.DeepLinkDispatcher
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
import fr.husi.utils.CrashHandler
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File
import javax.swing.JOptionPane
import javax.swing.JTextArea
import javax.swing.UIManager
import kotlin.system.exitProcess

private const val APP_NAME = "fr.husi"

fun main(args: Array<String>) = DesktopMain().main(args)

private class DesktopMain : CliktCommand(APP_NAME) {

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

    val deepLinks: List<String> by argument(
        name = "deep-link",
        help = "Deep links",
    ).multiple()

    override fun run() {
        taskId?.let {
            exitProcess(runTaskMode(it))
        }

        registerMacOSOpenUriHandler()
        initDesktopRuntime()
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
            val supportTray = remember { isTraySupported }
            var windowVisible by remember {
                mutableStateOf(!background || !supportTray)
            }

            val trayState = rememberTrayState()
            val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))

            fun openWindow() {
                windowVisible = true
                windowState.isMinimized = false
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
                LaunchedEffect(autoStart) {
                    if (shouldAutoConnectOnLaunch()) {
                        repository.startService()
                    }
                }
                if (supportTray) {
                    // In fact, whether on macOS, Windows, or Linux, the advanced tray consistently throws "java.lang.UnsupportedOperationException: java.awt.Menu doesn't support mnemonic."
                    val supportAdvancedTray = false
                    Tray(
                        icon = painterResource(Res.drawable.ic_service_active),
                        state = trayState,
                        tooltip = stringResource(Res.string.app_name),
                        onAction = ::openWindow,
                    ) {
                        val serviceStatus by BackendState.status.collectAsState()
                        Item(
                            text = serviceStatus.profileName ?: stringResource(Res.string.app_name),
                            mnemonic = if (supportAdvancedTray) {
                                'O'
                            } else {
                                null
                            },
                        ) {
                            openWindow()
                        }
                        Item(
                            text = stringResource(
                                if (serviceStatus.state == ServiceState.Connected) {
                                    Res.string.stop
                                } else {
                                    Res.string.start
                                },
                            ),
                            enabled = serviceStatus.state == ServiceState.Connected
                                    || serviceStatus.state == ServiceState.Stopped
                                    || serviceStatus.state == ServiceState.Idle,
                        ) {
                            when (serviceStatus.state) {
                                ServiceState.Stopped -> repository.startService()
                                ServiceState.Idle, ServiceState.Connected -> repository.stopService()
                                else -> {}
                            }
                        }
                        Menu(
                            text = stringResource(Res.string.service_mode),
                        ) {
                            val serviceMode by DataStore.configurationStore
                                .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
                                .collectAsState(Key.MODE_VPN)
                            CheckboxItem(
                                text = stringResource(Res.string.service_mode_proxy),
                                checked = serviceMode == Key.MODE_PROXY,
                            ) {
                                if (serviceMode != Key.MODE_PROXY) {
                                    DataStore.serviceMode = Key.MODE_PROXY
                                    repository.reloadService()
                                }
                            }
                            CheckboxItem(
                                text = stringResource(Res.string.service_mode_vpn),
                                checked = serviceMode == Key.MODE_VPN,
                            ) {
                                if (serviceMode != Key.MODE_VPN) {
                                    DataStore.serviceMode = Key.MODE_VPN
                                    repository.reloadService()
                                }
                            }
                        }
                        Item(
                            text = stringResource(Res.string.exit),
                            icon = if (supportAdvancedTray) {
                                painterResource(Res.drawable.close)
                            } else {
                                null
                            },
                            mnemonic = if (supportAdvancedTray) {
                                'E'
                            } else {
                                null
                            },
                            onClick = ::exitGracefully,
                        )
                    }
                }

                Window(
                    onCloseRequest = { windowVisible = false },
                    state = windowState,
                    visible = windowVisible,
                    title = stringResource(Res.string.app_name),
                    icon = painterResource(Res.drawable.ic_service_active),
                ) {
                    AppTheme {
                        MainScreen(moveToBackground = {})
                    }
                }
            }
        }
    }

    private fun shouldAutoConnectOnLaunch(): Boolean {
        return autoStart
                && DataStore.persistAcrossReboot
                && DataStore.selectedProxy > 0L
                && !DataStore.serviceState.started
    }

    private fun initDesktopRuntime() {
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
                    if (autoStart) -> exitApplication()

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
        val baseDir = baseDir ?: File(System.getProperty("user.home"), ".config").resolve("husi")
        baseDir.mkdirs()
        return DesktopRepository(baseDir)
    }

    private fun bootstrapDesktopRuntime(
        repository: DesktopRepository,
        startCommandServer: Boolean,
    ) {
        DesktopAutoStart.initialize()
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

private fun warnLibcoreLoadFailureAndExit(error: LinkageError): Nothing {
    val title = "Failed to load libcore"
    val message = buildString {
        appendLine("Husi could not load the libcore JNI library.")
        appendLine()
        appendLine("This usually means the desktop libcore package does not match this system,")
        appendLine("or developer made mistakes.")
        appendLine()
        appendLine("System: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        appendLine("Java: ${System.getProperty("java.version")}")
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
