package fr.husi.di

import fr.husi.compose.material3.DesktopPlatformMaterialApi
import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.compose.theme.standardPlatformThemeApi
import fr.husi.net.GrpcHttpFetcher
import fr.husi.net.GrpcWebDAVClient
import fr.husi.net.HttpFetcher
import fr.husi.net.WebDAVClient
import fr.husi.repository.DesktopRepository
import fr.husi.repository.LocalRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMaterialApi(): PlatformMaterialApi = DesktopPlatformMaterialApi

internal actual fun platformThemeApi(): PlatformThemeApi = standardPlatformThemeApi()

internal actual fun platformRepositoryModule(repository: LocalRepository): Module = module {
    single<LocalRepository> { repository }
    single<DesktopRepository> { repository as DesktopRepository }
}

private val desktopHttpModule = module {
    single<HttpFetcher> { GrpcHttpFetcher(coreClient = get()) }
    single<WebDAVClient> { GrpcWebDAVClient(coreClient = get()) }
}

internal actual fun platformKoinModules(): List<Module> = listOf(desktopHttpModule)

internal actual fun coreClientBasePath(repository: LocalRepository): String {
    return (repository as DesktopRepository).coreSocketBasePath
}
