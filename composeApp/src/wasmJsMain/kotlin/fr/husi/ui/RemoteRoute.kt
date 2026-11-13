package fr.husi.ui

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
internal sealed interface RemoteRoute : NavKey {
    @Serializable data object Setup : RemoteRoute

    @Serializable data object Stats : RemoteRoute

    @Serializable data object ProxySet : RemoteRoute

    @Serializable data object Connections : RemoteRoute

    @Serializable data object Logs : RemoteRoute

    @Serializable data object Rules : RemoteRoute
    @Serializable data object Providers : RemoteRoute
    @Serializable data object Dns : RemoteRoute

    @Serializable data object Tools : RemoteRoute

    @Serializable data object Settings : RemoteRoute

    @Serializable data object GeneralSettings : RemoteRoute

    @Serializable data object About : RemoteRoute

    @Serializable data object Libraries : RemoteRoute

    @Serializable data object Servers : RemoteRoute

    @Serializable data class ServerEdit(val id: Long? = null) : RemoteRoute

    @Serializable data object Stun : RemoteRoute

    @Serializable data object NetworkQuality : RemoteRoute

    @Serializable data class Endpoint(val tag: String) : RemoteRoute

    @Serializable data class Peer(val tag: String, val id: String) : RemoteRoute

    @Serializable data class ExitNode(val tag: String) : RemoteRoute

    @Serializable data class Inbox(val tag: String) : RemoteRoute

    @Serializable data object Ssh : RemoteRoute

    companion object {
        val topLevel: Set<NavKey> = setOf(Stats, ProxySet, Connections, Logs, Rules, Settings)
        val savedStateConfiguration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Setup::class, Setup.serializer())
                    subclass(Stats::class, Stats.serializer())
                    subclass(ProxySet::class, ProxySet.serializer())
                    subclass(Connections::class, Connections.serializer())
                    subclass(Logs::class, Logs.serializer())
                    subclass(Rules::class, Rules.serializer())
                    subclass(Providers::class, Providers.serializer())
                    subclass(Dns::class, Dns.serializer())
                    subclass(Tools::class, Tools.serializer())
                    subclass(Servers::class, Servers.serializer())
                    subclass(Settings::class, Settings.serializer())
                    subclass(GeneralSettings::class, GeneralSettings.serializer())
                    subclass(About::class, About.serializer())
                    subclass(Libraries::class, Libraries.serializer())
                    subclass(ServerEdit::class, ServerEdit.serializer())
                    subclass(Stun::class, Stun.serializer())
                    subclass(NetworkQuality::class, NetworkQuality.serializer())
                    subclass(Endpoint::class, Endpoint.serializer())
                    subclass(Peer::class, Peer.serializer())
                    subclass(ExitNode::class, ExitNode.serializer())
                    subclass(Inbox::class, Inbox.serializer())
                    subclass(Ssh::class, Ssh.serializer())
                }
            }
        }
    }
}

/** A selected server is required for every destination outside setup. */
internal fun remoteBackStack(backStack: List<NavKey>, hasServer: Boolean): List<NavKey> {
    if (!hasServer) return listOf(RemoteRoute.Setup)
    return backStack.filterNot { it == RemoteRoute.Setup }.ifEmpty { listOf(RemoteRoute.Stats) }
}

/** Validate restored destinations whenever the selected controller protocol changes. */
internal fun remoteRouteAvailable(route: NavKey, clash: Boolean): Boolean = when (route) {
    RemoteRoute.Rules, RemoteRoute.Providers, RemoteRoute.Dns -> clash
    RemoteRoute.Stun, RemoteRoute.NetworkQuality, RemoteRoute.Ssh,
    is RemoteRoute.Endpoint, is RemoteRoute.Peer, is RemoteRoute.ExitNode, is RemoteRoute.Inbox -> !clash
    else -> true
}
