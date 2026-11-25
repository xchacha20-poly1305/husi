package fr.husi.di

import fr.husi.bg.ServiceEventMirror
import fr.husi.bg.withBackgroundProcess
import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.material3.TvPlatformMaterialApi
import fr.husi.compose.material3.standardPlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.compose.theme.TvPlatformThemeApi
import fr.husi.compose.theme.standardPlatformThemeApi
import fr.husi.database.DataStore
import fr.husi.net.canUseHttpEngineForCert
import fr.husi.net.createHttpEngineCallFactory
import fr.husi.net.isHttpEngineSupported
import fr.husi.repository.AndroidRepository
import fr.husi.repository.LocalRepository
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Call
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

internal actual fun platformRepositoryModule(repository: LocalRepository): Module = module {
    val androidRepository = repository as? AndroidRepository
        ?: error("Android platform requires AndroidRepository, got ${repository::class.qualifiedName}")
    single<AndroidRepository> { androidRepository }
    single<LocalRepository> { get<AndroidRepository>() }
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

internal actual suspend fun prepareHttpRoots() = withBackgroundProcess {}

internal actual fun platformDirectCallFactory(repository: LocalRepository): suspend (isHttps: Boolean) -> Call.Factory? {
    if (!isHttpEngineSupported()) {
        return { null }
    }
    val context = (repository as AndroidRepository).context
    val factory by lazy { createHttpEngineCallFactory(context) }
    return { isHttps ->
        if (!isHttps
            || canUseHttpEngineForCert(DataStore.certProvider.get(), DataStore.httpEngineAllowSystemCert.get())
        ) {
            factory
        } else {
            null
        }
    }
}

internal actual fun coreClientBasePath(repository: LocalRepository): String =
    (repository as AndroidRepository).context.filesDir.path
