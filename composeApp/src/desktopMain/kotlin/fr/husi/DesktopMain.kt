package fr.husi

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.compose.getPlainText
import fr.husi.compose.theme.AppTheme
import fr.husi.database.DataStore
import fr.husi.keyevent.KeyEventManagerDesktop
import fr.husi.keyevent.LocalKeyEventManager
import fr.husi.keyevent.isTypeControlPressed
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.libcore.Libcore
import fr.husi.libcore.loadCA
import fr.husi.repository.DesktopRepository
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.resources.exit
import fr.husi.resources.ic_service_active
import fr.husi.resources.ic_service_rest
import fr.husi.resources.service_mode
import fr.husi.resources.service_mode_proxy
import fr.husi.resources.service_mode_vpn
import fr.husi.resources.start
import fr.husi.resources.stop
import fr.husi.ui.MainScreen
import fr.husi.ui.MainViewModel
import fr.husi.utils.CrashHandler
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import androidx.compose.ui.input.key.Key as InputKey

private const val MIN_LOG_LEVEL = 0
private const val MAX_LOG_LEVEL = 6
private const val PreferenceNodePropertyName = "me.zhanghai.compose.preference.node"
private const val PreferenceNodeName = "/fr/husi/preference"

fun main(args: Array<String>) {
    val desktopArgs = parseDesktopStartupArgs(args)
    initDesktopRuntime(desktopArgs)

    application {
        val keyEventManager = remember { KeyEventManagerDesktop() }

        var windowVisible by remember { mutableStateOf(true) }

        val trayState = rememberTrayState()
        val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))

        fun openWindow() {
            windowVisible = true
            windowState.isMinimized = false
        }

        Tray(
            icon = painterResource(Res.drawable.ic_service_rest),
            state = trayState,
            tooltip = stringResource(Res.string.app_name),
            onAction = ::openWindow,
        ) {
            // TODO mnemonic and icon is not supported on some desktop environments (GNOME)
            val serviceStatus by BackendState.status.collectAsState()
            Item(
                text = serviceStatus.profileName ?: stringResource(Res.string.app_name),
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
                    ServiceState.Stopped -> repo.startService()
                    ServiceState.Idle,ServiceState.Connected -> repo.stopService()
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
                        repo.reloadService()
                    }
                }
                CheckboxItem(
                    text = stringResource(Res.string.service_mode_vpn),
                    checked = serviceMode == Key.MODE_VPN,
                ) {
                    if (serviceMode != Key.MODE_VPN) {
                        DataStore.serviceMode = Key.MODE_VPN
                        repo.reloadService()
                    }
                }
            }
            Item(
                text = stringResource(Res.string.exit),
                // icon = painterResource(Res.drawable.close),
                // mnemonic = 'E',
                onClick = ::exitApplication,
            )
        }

        Window(
            onCloseRequest = { windowVisible = false },
            state = windowState,
            visible = windowVisible,
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.ic_service_active),
            onKeyEvent = { keyEvent ->
                if (windowState.isMinimized || !windowVisible) return@Window false
                if (keyEvent.type != KeyEventType.KeyDown) return@Window false
                keyEventManager.dispatch(keyEvent)
            },
        ) {
            val viewModel = viewModel { MainViewModel() }
            val clipboard = LocalClipboard.current

            fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
                if (!keyEvent.isTypeControlPressed) return false
                if (keyEvent.key != InputKey.V) return false
                runOnDefaultDispatcher {
                    clipboard.getPlainText()?.let {
                        viewModel.importFromUri(it)
                    }
                }
                return true
            }

            DisposableEffect(Unit) {
                keyEventManager.register(::handleKeyEvent)
                onDispose {
                    keyEventManager.unregister(::handleKeyEvent)
                }
            }
            AppTheme {
                CompositionLocalProvider(
                    LocalKeyEventManager provides keyEventManager,
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        moveToBackground = {},
                    )
                }
            }
        }
    }
}

private data class DesktopStartupArgs(
    val baseDir: File?,
    val logLevelOverride: Int?,
)

private fun parseDesktopStartupArgs(args: Array<String>): DesktopStartupArgs {
    val parser = ArgParser("husi")
    val baseDir by parser.option(
        type = ArgType.String,
        fullName = "dir",
        shortName = "d",
        description = "Data directory",
    )
    val logLevel by parser.option(
        type = ArgType.Int,
        fullName = "log-level",
        shortName = "l",
        description = "Log level override (0-6)",
    )
    parser.parse(args)
    return DesktopStartupArgs(
        baseDir = baseDir?.blankAsNull()?.let(::File),
        logLevelOverride = logLevel?.takeIf { it in MIN_LOG_LEVEL..MAX_LOG_LEVEL },
    )
}

private fun initDesktopRuntime(startupArgs: DesktopStartupArgs) {
    // Fix jar package
    System.setProperty(PreferenceNodePropertyName, PreferenceNodeName)

    val baseDir = startupArgs.baseDir ?: File(System.getProperty("user.home"), ".husi")
    baseDir.mkdirs()
    repo = DesktopRepository(baseDir)
    Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

    val cacheDir = repo.cacheDir.absolutePath + "/"
    val filesDir = repo.filesDir.absolutePath + "/"
    val externalAssetsDir = repo.externalAssetsDir.absolutePath + "/"

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
        startupArgs.logLevelOverride ?: DataStore.logLevel,
        isOfficialProvider,
        DataStore.isExpert,
    )
    loadCA(DataStore.certProvider)
    repo.boxService?.start()
}
