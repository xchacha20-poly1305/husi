package fr.husi.di

import fr.husi.bg.ServiceEventMirror
import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.material3.TvPlatformMaterialApi
import fr.husi.compose.material3.standardPlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.compose.theme.TvPlatformThemeApi
import fr.husi.compose.theme.standardPlatformThemeApi
import fr.husi.repository.AndroidRepository
import fr.husi.repository.Repository
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMaterialApi(): PlatformMaterialApi {
    return if (resolveRepository().isTv) {
        TvPlatformMaterialApi
    } else {
        standardPlatformMaterialApi()
    }
}

internal actual fun platformThemeApi(): PlatformThemeApi {
    return if (resolveRepository().isTv) {
        TvPlatformThemeApi
    } else {
        standardPlatformThemeApi()
    }
}

internal actual fun platformRepositoryModule(repository: Repository): Module = module {
    val androidRepository = repository as? AndroidRepository
        ?: error("Android platform requires AndroidRepository, got ${repository::class.qualifiedName}")
    single<AndroidRepository> { androidRepository }
    single<Repository> { get<AndroidRepository>() }
    if (repository.isMainProcess) {
        single {
            ServiceEventMirror(
                coreClient = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
    }
}

internal actual fun platformKoinModules(): List<Module> = listOf(androidNavigationModule)

internal actual fun coreClientBasePath(repository: Repository): String? = null
