package fr.husi.ui

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
internal sealed interface RemoteRoute : NavKey {
    @Serializable data object Setup : RemoteRoute

    @Serializable data object Dashboard : RemoteRoute

    @Serializable data object Logs : RemoteRoute

    @Serializable data object Tools : RemoteRoute

    @Serializable data object Settings : RemoteRoute

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
        val topLevel: Set<NavKey> = setOf(Dashboard, Logs, Tools, Servers, Settings)
        val savedStateConfiguration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Setup::class, Setup.serializer())
                    subclass(Dashboard::class, Dashboard.serializer())
                    subclass(Logs::class, Logs.serializer())
                    subclass(Tools::class, Tools.serializer())
                    subclass(Servers::class, Servers.serializer())
                    subclass(Settings::class, Settings.serializer())
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
    return backStack.filterNot { it == RemoteRoute.Setup }.ifEmpty { listOf(RemoteRoute.Dashboard) }
}
