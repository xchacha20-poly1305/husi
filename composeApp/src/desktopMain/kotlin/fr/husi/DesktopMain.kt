package fr.husi

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import fr.husi.bg.ServiceState
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

    val deepLinks: List<String> by argument(
        name = "deep-link",
        help = "Deep links",
    ).multiple()

    override fun run() {
        registerMacOSOpenUriHandler()
        initDesktopRuntime()
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
        // Fix jar package
        System.setProperty(PREFERENCE_NODE_PROPERTY_NAME, PREFERENCE_NODE_NAME)

        val baseDir = baseDir ?: File(System.getProperty("user.home"), ".config").resolve("husi")
        baseDir.mkdirs()
        val repository = DesktopRepository(baseDir)
        DesktopAutoStart.initialize()
        initHusiKoin(repository)
        val filesDir = repository.filesDir.invariantDirectoryPathString()

        if (!many) {
            when (checkExistingInstance(filesDir, deepLinks)) {
                ExistingInstanceCheckResult.NotFound -> Unit
                ExistingInstanceCheckResult.ExistsNoDeepLink
                    if (autoStart) -> exitApplication()

                ExistingInstanceCheckResult.ExistsNoDeepLink,
                ExistingInstanceCheckResult.ExistsForwardFailed,
                    -> warnForExistInstanceAndExit(filesDir)

                ExistingInstanceCheckResult.ExistsForwarded -> exitApplication()
            }
        }

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        val cacheDir = repository.cacheDir.invariantDirectoryPathString()
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
            cacheDir,
            filesDir,
            externalAssetsDir,
            DataStore.logMaxLine,
            logLevel ?: DataStore.logLevel,
            isOfficialProvider,
            DataStore.isExpert,
        )
        loadCA(DataStore.certProvider)
        repository.boxService?.start()
    }
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

private enum class ExistingInstanceCheckResult {
    NotFound,
    ExistsNoDeepLink,
    ExistsForwarded,
    ExistsForwardFailed,
}

private fun checkExistingInstance(
    socketBasePath: String,
    deepLinks: List<String>,
): ExistingInstanceCheckResult {
    val client = runCatching {
        Libcore.newClient(socketBasePath)
    }.getOrNull() ?: return ExistingInstanceCheckResult.NotFound
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

private fun forwardDeepLinks(client: Client, deepLinks: List<String>): Boolean {
    return runCatching {
        client.importDeepLinks(deepLinks.toStringIterator(deepLinks.size))
    }.onFailure {
        Logs.e(it)
    }.isSuccess
}

private fun warnForExistInstanceAndExit(socketBasePath: String) {
    val repository = resolveDesktopRepository()
    val socketPath = socketBasePath + Libcore.Socket
    val title = runBlocking { repository.getString(Res.string.instance_already_running_title) }
    val message = runBlocking {
        repository.getString(Res.string.instance_already_running, socketPath)
    }
    try {
        JOptionPane.showMessageDialog(
            null,
            message,
            title,
            JOptionPane.WARNING_MESSAGE,
        )
    } catch (e: Exception) {
        System.err.println("$title: $message")
        System.err.println(e.message)
    }
    exitProcess(1)
}
