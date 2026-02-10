package fr.husi

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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
import fr.husi.ui.MainScreen
import fr.husi.ui.MainViewModel
import fr.husi.utils.CrashHandler
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import java.io.File

private const val MIN_LOG_LEVEL = 0
private const val MAX_LOG_LEVEL = 6

fun main(args: Array<String>) {
    initDesktopRuntime(parseDesktopStartupArgs(args))

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
        ) {
            val viewModel = viewModel { MainViewModel() }
            AppTheme {
                ProvidePermissionPlatform {
                    MainScreen(
                        viewModel = viewModel,
                        exit = ::exitApplication,
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
        shortName = "d",
        description = "Data directory",
    )
    val logLevel by parser.option(
        type = ArgType.Int,
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
