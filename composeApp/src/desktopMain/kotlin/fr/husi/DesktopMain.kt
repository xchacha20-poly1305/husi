package fr.husi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.theme.AppTheme
import fr.husi.database.DataStore
import fr.husi.ktx.blankAsNull
import fr.husi.libcore.Libcore
import fr.husi.libcore.loadCA
import fr.husi.permission.ProvidePermissionPlatform
import fr.husi.repository.DesktopRepository
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.resources.exit
import fr.husi.resources.ic_service_active
import fr.husi.resources.ic_service_rest
import fr.husi.resources.show_window
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

private const val MIN_LOG_LEVEL = 0
private const val MAX_LOG_LEVEL = 6

fun main(args: Array<String>) {
    val desktopArgs = parseDesktopStartupArgs(args)
    initDesktopRuntime(desktopArgs)

    application {
        val viewModel = viewModel { MainViewModel() }
        
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
            Item(
                text = stringResource(Res.string.show_window),
                // mnemonic = 'S',
                onClick = ::openWindow,
            )
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
        ) {
            AppTheme {
                ProvidePermissionPlatform {
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
}
