package fr.husi.di

import fr.husi.bg.AppUpdateAutoChecker
import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.core.CoreClient
import fr.husi.core.CoreVersionCache
import fr.husi.core.KurpcCoreClient
import fr.husi.core.rootCertificatesSocketFactory
import fr.husi.core.remote.RemoteClientFactory
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.SagerDatabase
import fr.husi.libcore.Libcore
import fr.husi.repository.Repository
import fr.husi.ui.ImportLinkInteractor
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openvpn.OpenVPNAuthController
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private fun commonUiModule() = module {
    single<PlatformMaterialApi> { platformMaterialApi() }
    single<PlatformThemeApi> { platformThemeApi() }
    // Resolve the socket base path on every dial so CoreHostController can
    // switch between the session working dir and the system daemon path.
    single<CoreClient> {
        val repository = get<Repository>()
        KurpcCoreClient.local { coreClientBasePath(repository) }
    }
    single { CoreVersionCache(coreClient = get()) }
    single {
        // Every app process runs loadCA(), which writes the roots Go trusts to this file.
        val rootCertificates = get<Repository>().externalAssetsDir.resolve(Libcore.PluginCaFile)
        RemoteControlManager(
            localClient = get(),
            dao = SagerDatabase.remoteServerDao,
            remoteClientFactory = RemoteClientFactory { url, secret ->
                KurpcCoreClient.remote(url, secret) { rootCertificatesSocketFactory(rootCertificates) }
            },
        )
    }
    single { AppUpdateAutoChecker() }
    singleOf(::ImportLinkInteractor)
    singleOf(::OpenConnectAuthController)
    single { OpenVPNAuthController(coreClient = get()) }
}

/**
 * Directory that holds `api.sock` for [KurpcCoreClient] (or, for the Windows daemon, the pipe
 * path itself). Android: the files dir, which `initCore` makes the core's internal assets path.
 * Desktop: the session host working dir under the data directory, or the daemon's.
 */
internal expect fun coreClientBasePath(repository: Repository): String

internal expect fun platformMaterialApi(): PlatformMaterialApi
internal expect fun platformThemeApi(): PlatformThemeApi
internal expect fun platformRepositoryModule(repository: Repository): Module
internal expect fun platformKoinModules(): List<Module>

fun initHusiKoin(repository: Repository) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        modules(
            listOf(platformRepositoryModule(repository), commonUiModule(), commonNavigationModule) +
                platformKoinModules(),
        )
    }
}
