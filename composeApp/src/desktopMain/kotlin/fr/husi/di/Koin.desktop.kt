package fr.husi.di

import fr.husi.compose.material3.DesktopPlatformMaterialApi
import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.compose.theme.standardPlatformThemeApi
import fr.husi.repository.DesktopRepository
import fr.husi.repository.Repository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMaterialApi(): PlatformMaterialApi = DesktopPlatformMaterialApi

internal actual fun platformThemeApi(): PlatformThemeApi = standardPlatformThemeApi()

internal actual fun platformRepositoryModule(repository: Repository): Module = module {
    single<Repository> { repository }
    single<DesktopRepository> { repository as DesktopRepository }
}

internal actual fun platformKoinModules(): List<Module> = emptyList()

internal actual fun coreClientBasePath(repository: Repository): String? {
    return (repository as DesktopRepository).coreSocketBasePath
}
