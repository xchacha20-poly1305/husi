package fr.husi.di

import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.material3.standardPlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.compose.theme.standardPlatformThemeApi
import fr.husi.repository.DesktopRepository
import fr.husi.repository.LocalRepository
import okhttp3.Call
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMaterialApi(): PlatformMaterialApi = standardPlatformMaterialApi()

internal actual fun platformThemeApi(): PlatformThemeApi = standardPlatformThemeApi()

internal actual fun platformRepositoryModule(repository: LocalRepository): Module = module {
    single<LocalRepository> { repository }
    single<DesktopRepository> { repository as DesktopRepository }
}

internal actual fun platformKoinModules(): List<Module> = emptyList()

internal actual suspend fun prepareHttpRoots() = Unit

internal actual fun platformDirectCallFactory(repository: LocalRepository): suspend (isHttps: Boolean) -> Call.Factory? {
    return { null }
}

internal actual fun coreClientBasePath(repository: LocalRepository): String {
    return (repository as DesktopRepository).coreSocketBasePath
}
