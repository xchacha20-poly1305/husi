package fr.husi.di

import fr.husi.PLUGIN_CA_FILE
import fr.husi.bg.AppUpdateAutoChecker
import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.core.CoreClient
import fr.husi.core.CoreVersionCache
import fr.husi.core.WireCoreClient
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.SagerDatabase
import fr.husi.net.AppHttpClient
import fr.husi.net.HttpFetcher
import fr.husi.net.OkHttpFetcher
import fr.husi.net.OkHttpWebDAVClient
import fr.husi.net.WebDAVClient
import fr.husi.repository.LocalRepository
import fr.husi.repository.Repository
import fr.husi.tailscale.TaildropSendManager
import fr.husi.tailscale.TailscaleSSHSessionManager
import fr.husi.tailscale.TailscaleStatusRepository
import fr.husi.tailscale.dashboardNoticeCounts
import fr.husi.ui.ImportLinkInteractor
import fr.husi.ui.dashboard.DashboardNoticeTracker
import fr.husi.ui.dashboard.asNoticeCounts
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openvpn.OpenVPNAuthController
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private fun commonUiModule() = module {
    single<Repository> { get<LocalRepository>() }
    single<PlatformMaterialApi> { platformMaterialApi() }
    single<PlatformThemeApi> { platformThemeApi() }
    // Resolve the socket base path on channel open so CoreHostController can
    // switch between the session working dir and the system daemon path.
    single<CoreClient> {
        val repository = get<LocalRepository>()
        WireCoreClient.local { coreClientBasePath(repository) }
    }
    single {
        AppHttpClient(
            get<LocalRepository>().externalAssetsDir.resolve(PLUGIN_CA_FILE),
            ::prepareHttpRoots,
        )
    }
    single<HttpFetcher> {
        val core = get<CoreClient>()
        // TODO: Decrypt age-encrypted subscriptions directly with a Kotlin age implementation.
        OkHttpFetcher(get()) { requests ->
            withAgeCore {
                core.decryptAge(requests)
            }
        }
    }
    single<WebDAVClient> { OkHttpWebDAVClient(get()) }
    single { CoreVersionCache(coreClient = get()) }
    single {
        // Written at startup from the user's certificate provider: by loadCA() on Android, by
        // the core host controller on desktop.
        val rootCertificates = get<LocalRepository>().externalAssetsDir.resolve(PLUGIN_CA_FILE)
        RemoteControlManager(
            localClient = get(),
            dao = SagerDatabase.remoteServerDao,
            remoteClientFactory = { url, secret ->
                WireCoreClient.remote(url, secret, roots = rootCertificates)
            },
        )
    }
    single { AppUpdateAutoChecker() }
    singleOf(::ImportLinkInteractor)
    single { OpenConnectAuthController(targets = get<RemoteControlManager>().runningTarget) }
    single { OpenVPNAuthController(targets = get<RemoteControlManager>().runningTarget) }
    single {
        DashboardNoticeTracker(
            noticeCounts = listOf(
                get<OpenConnectAuthController>().pendingChallengeKeys.map { it.asNoticeCounts() },
                get<OpenVPNAuthController>().pendingChallengeKeys.map { it.asNoticeCounts() },
                get<TailscaleStatusRepository>().snapshot.map { it.endpoints.dashboardNoticeCounts() },
            ),
        )
    }
    single { TailscaleStatusRepository(targets = get<RemoteControlManager>().runningTarget) }
    single { TaildropSendManager(targets = get<RemoteControlManager>().runningTarget) }
    single { TailscaleSSHSessionManager(targets = get<RemoteControlManager>().runningTarget) }
}

/**
 * Directory that holds `api.sock` for [WireCoreClient] (or, for the Windows daemon, the pipe
 * path itself). Android: the files dir, which `initCore` makes the core's internal assets path.
 * Desktop: the session host working dir under the data directory, or the daemon's.
 */
internal expect fun coreClientBasePath(repository: LocalRepository): String

internal expect suspend fun prepareHttpRoots()
internal expect fun <T> withAgeCore(decrypt: () -> Flow<T>): Flow<T>

internal expect fun platformMaterialApi(): PlatformMaterialApi
internal expect fun platformThemeApi(): PlatformThemeApi
internal expect fun platformRepositoryModule(repository: LocalRepository): Module
internal expect fun platformKoinModules(): List<Module>

fun initHusiKoin(repository: LocalRepository) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        modules(
            listOf(platformRepositoryModule(repository), commonUiModule(), commonNavigationModule) +
                platformKoinModules(),
        )
    }
}
