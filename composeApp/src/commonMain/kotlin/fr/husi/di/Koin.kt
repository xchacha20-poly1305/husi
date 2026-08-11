package fr.husi.di

import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.core.BridgeCoreClient
import fr.husi.core.CoreClient
import fr.husi.libcore.HttpClientFactory
import fr.husi.libcore.Libcore
import fr.husi.libcore.LibcoreHttpClientFactory
import fr.husi.repository.Repository
import fr.husi.ui.ImportLinkInteractor
import fr.husi.ui.openconnect.OpenConnectAuthController
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private fun commonUiModule() = module {
    single<PlatformMaterialApi> { platformMaterialApi() }
    single<PlatformThemeApi> { platformThemeApi() }
    single<HttpClientFactory> { LibcoreHttpClientFactory }
    // Resolve the socket base path on every dial so CoreHostController can
    // switch between the session working dir and the system daemon path.
    single<CoreClient> {
        val repository = get<Repository>()
        BridgeCoreClient(
            basePath = null,
            bridgeFactory = { Libcore.newBridgeClient(coreClientBasePath(repository)) },
        )
    }
    singleOf(::ImportLinkInteractor)
    singleOf(::OpenConnectAuthController)
}

/**
 * Directory that holds `api.sock` for [BridgeCoreClient]. Null keeps the Go
 * default (`internalAssetsPath` / files dir). Desktop points at the session
 * host working dir under the data directory.
 */
internal expect fun coreClientBasePath(repository: Repository): String?

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
