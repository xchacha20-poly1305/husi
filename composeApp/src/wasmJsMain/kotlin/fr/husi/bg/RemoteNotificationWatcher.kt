package fr.husi.bg

import androidx.navigation3.runtime.NavKey
import fr.husi.database.DataStore
import fr.husi.database.RemoteBackend
import fr.husi.remote.RemoteState
import fr.husi.repository.Repository
import fr.husi.resources.Res
import fr.husi.resources.auth_required
import fr.husi.resources.openconnect_authentication
import fr.husi.resources.openvpn_authentication
import fr.husi.resources.taildrop_received_notification
import fr.husi.resources.tailscale_state_needs_login
import fr.husi.resources.tailscale_with_tag
import fr.husi.tailscale.TAILSCALE_STATE_NEEDS_LOGIN
import fr.husi.tailscale.TailscaleEndpointState
import fr.husi.tailscale.TailscaleNotice
import fr.husi.tailscale.TailscaleNoticeSnapshot
import fr.husi.tailscale.tailscaleNotices
import fr.husi.tailscale.toNoticeSnapshots
import fr.husi.ui.RemoteRoute
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OpenConnectEndpointState
import fr.husi.vpn.OpenVPNEndpointState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

internal sealed interface RemoteNotificationAction {
    data class OpenOpenConnectAuth(
        val endpointTag: String,
        val challengeId: String,
    ) : RemoteNotificationAction

    data class OpenOpenVPNAuth(
        val endpointTag: String,
        val challengeId: String,
    ) : RemoteNotificationAction

    data class OpenTailscaleEndpoint(
        val endpointTag: String,
    ) : RemoteNotificationAction

    data class OpenTaildropInbox(
        val endpointTag: String,
    ) : RemoteNotificationAction
}

internal class RemoteNotificationWatcher(
    private val scope: CoroutineScope,
    private val remote: RemoteState,
    private val notifications: BrowserNotificationClient,
    private val repository: Repository,
    private val enabledFlow: Flow<Boolean> = DataStore.browserNotifications.flow(),
) {
    private data class ServerContextKey(
        val id: Long,
        val url: String,
        val secret: String,
        val backend: RemoteBackend,
    )

    private companion object {
        const val NOTIFICATION_ICON = "icons/icon-192.png"
        const val TAG_OPENCONNECT_PREFIX = "husi-openconnect"
        const val TAG_OPENVPN_PREFIX = "husi-openvpn"
        const val TAG_TAILSCALE_LOGIN_PREFIX = "husi-tailscale-login"
        const val TAG_TAILDROP_PREFIX = "husi-taildrop"
    }

    val clickActions: SharedFlow<RemoteNotificationAction>
        field = MutableSharedFlow(extraBufferCapacity = 8)

    var currentRoute: NavKey? = null

    private var enabled = false
    private var currentServerKey: ServerContextKey? = null
    private var contextGeneration = 0
    private var previousOpenConnectPending = emptyMap<String, String>()
    private var previousOpenVPNPending = emptyMap<String, String>()
    private var previousTailscaleSnapshots = emptyMap<String, TailscaleNoticeSnapshot>()
    private val activeHandles = mutableMapOf<String, BrowserNotificationHandle>()
    private val watcherScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]),
    )

    init {
        watcherScope.launch {
            enabledFlow.collect { isEnabled ->
                enabled = isEnabled
                if (!isEnabled) {
                    closeAll()
                }
            }
        }
        watcherScope.launch {
            notifications.permission.collect { permission ->
                if (permission != BrowserNotificationPermission.GRANTED) {
                    closeAll()
                }
            }
        }
        watcherScope.launch {
            remote.activeNativeServer.collect {
                syncServerContext()
            }
        }
        watcherScope.launch {
            remote.liveOpenConnectEndpoints.filterNotNull().collect { endpoints ->
                handleOpenConnect(endpoints)
            }
        }
        watcherScope.launch {
            remote.liveOpenVPNEndpoints.filterNotNull().collect { endpoints ->
                handleOpenVPN(endpoints)
            }
        }
        watcherScope.launch {
            remote.liveTailscaleEndpoints.filterNotNull().collect { endpoints ->
                handleTailscale(endpoints)
            }
        }
    }

    fun close() {
        watcherScope.cancel()
        closeAll()
    }

    private fun syncServerContext(): ServerContextKey? {
        val server = remote.activeNativeServer.value
        val nextKey = server?.let { ServerContextKey(it.id, it.url, it.secret, it.backend) }
        if (nextKey != currentServerKey) {
            currentServerKey = nextKey
            resetContext()
        }
        return nextKey
    }

    private fun resetContext() {
        contextGeneration++
        previousOpenConnectPending = emptyMap()
        previousOpenVPNPending = emptyMap()
        previousTailscaleSnapshots = emptyMap()
        closeAll()
    }

    private fun canNotify(): Boolean =
        enabled &&
            currentServerKey != null &&
            notifications.permission.value == BrowserNotificationPermission.GRANTED

    private fun handleOpenConnect(endpoints: List<OpenConnectEndpointState>) {
        val serverKey = syncServerContext() ?: return
        val generation = contextGeneration
        val currentPending = buildMap {
            for (endpoint in endpoints) {
                val challenge = endpoint.authChallenge ?: continue
                if (endpoint.state == OPENCONNECT_STATE_AUTH_PENDING) {
                    put(endpoint.tag, challenge.id)
                }
            }
        }
        for ((tag, oldId) in previousOpenConnectPending) {
            if (currentPending[tag] != oldId) {
                closeTag(openConnectTag(serverKey.id, tag))
            }
        }
        val newlyPending = buildList {
            for ((tag, newId) in currentPending) {
                if (previousOpenConnectPending[tag] != newId) {
                    add(tag to newId)
                }
            }
        }
        previousOpenConnectPending = currentPending
        if (newlyPending.isEmpty() || !canNotify() || notifications.isPageFocused()) {
            return
        }
        for ((tag, challengeId) in newlyPending) {
            watcherScope.launch {
                val title = formatTitle(repository.getString(Res.string.openconnect_authentication))
                val body = "${repository.getString(Res.string.auth_required)}: $tag"
                if (generation != contextGeneration || currentServerKey != serverKey) return@launch
                if (!canNotify() || notifications.isPageFocused()) return@launch
                if (previousOpenConnectPending[tag] != challengeId) return@launch
                postNotification(
                    tag = openConnectTag(serverKey.id, tag),
                    title = title,
                    body = body,
                    onClick = {
                        clickActions.tryEmit(
                            RemoteNotificationAction.OpenOpenConnectAuth(tag, challengeId),
                        )
                    },
                )
            }
        }
    }

    private fun handleOpenVPN(endpoints: List<OpenVPNEndpointState>) {
        val serverKey = syncServerContext() ?: return
        val generation = contextGeneration
        val currentPending = buildMap {
            for (endpoint in endpoints) {
                val challenge = endpoint.challenge ?: continue
                if (endpoint.state == OPENVPN_STATE_AUTH_PENDING) {
                    put(endpoint.tag, challenge.id)
                }
            }
        }
        for ((tag, oldId) in previousOpenVPNPending) {
            if (currentPending[tag] != oldId) {
                closeTag(openVpnTag(serverKey.id, tag))
            }
        }
        val newlyPending = buildList {
            for ((tag, newId) in currentPending) {
                if (previousOpenVPNPending[tag] != newId) {
                    add(tag to newId)
                }
            }
        }
        previousOpenVPNPending = currentPending
        if (newlyPending.isEmpty() || !canNotify() || notifications.isPageFocused()) {
            return
        }
        for ((tag, challengeId) in newlyPending) {
            watcherScope.launch {
                val title = formatTitle(repository.getString(Res.string.openvpn_authentication))
                val body = "${repository.getString(Res.string.auth_required)}: $tag"
                if (generation != contextGeneration || currentServerKey != serverKey) return@launch
                if (!canNotify() || notifications.isPageFocused()) return@launch
                if (previousOpenVPNPending[tag] != challengeId) return@launch
                postNotification(
                    tag = openVpnTag(serverKey.id, tag),
                    title = title,
                    body = body,
                    onClick = {
                        clickActions.tryEmit(
                            RemoteNotificationAction.OpenOpenVPNAuth(tag, challengeId),
                        )
                    },
                )
            }
        }
    }

    private fun handleTailscale(endpoints: List<TailscaleEndpointState>) {
        val serverKey = syncServerContext() ?: return
        val generation = contextGeneration
        val notices = tailscaleNotices(previousTailscaleSnapshots, endpoints)
        previousTailscaleSnapshots = endpoints.toNoticeSnapshots()

        val byTag = endpoints.associateBy { it.endpointTag }
        val loginPrefix = tailscaleLoginPrefix(serverKey.id)
        val taildropPrefix = taildropPrefix(serverKey.id)
        for (notificationTag in activeHandles.keys.toList()) {
            if (notificationTag.startsWith(loginPrefix)) {
                val endpointTag = notificationTag.removePrefix(loginPrefix)
                val activeLogin = byTag[endpointTag]?.let {
                    it.backendState == TAILSCALE_STATE_NEEDS_LOGIN && it.authUrl.isNotEmpty()
                } == true
                if (!activeLogin) {
                    closeTag(notificationTag)
                }
            } else if (notificationTag.startsWith(taildropPrefix)) {
                val endpointTag = notificationTag.removePrefix(taildropPrefix)
                if ((byTag[endpointTag]?.unreadFileCount ?: 0) <= 0) {
                    closeTag(notificationTag)
                }
            }
        }

        if (notices.isEmpty() || !canNotify()) {
            return
        }
        for (notice in notices) {
            when (notice) {
                is TailscaleNotice.FilesReceived -> {
                    if (notifications.isPageFocused() &&
                        currentRoute == RemoteRoute.Inbox(notice.endpointTag)
                    ) {
                        continue
                    }
                    watcherScope.launch {
                        val title = formatTitle(
                            repository.getString(Res.string.tailscale_with_tag, notice.endpointTag),
                        )
                        val body = repository.getPluralString(
                            Res.plurals.taildrop_received_notification,
                            notice.count,
                            notice.count,
                        )
                        if (generation != contextGeneration || currentServerKey != serverKey) return@launch
                        if (!canNotify()) return@launch
                        if (notifications.isPageFocused() &&
                            currentRoute == RemoteRoute.Inbox(notice.endpointTag)
                        ) {
                            return@launch
                        }
                        if ((previousTailscaleSnapshots[notice.endpointTag]?.unreadFileCount ?: 0) <= 0) {
                            return@launch
                        }
                        postNotification(
                            tag = taildropTag(serverKey.id, notice.endpointTag),
                            title = title,
                            body = body,
                            onClick = {
                                clickActions.tryEmit(
                                    RemoteNotificationAction.OpenTaildropInbox(notice.endpointTag),
                                )
                            },
                        )
                    }
                }

                is TailscaleNotice.NeedsLogin -> {
                    if (notifications.isPageFocused() &&
                        currentRoute == RemoteRoute.Endpoint(notice.endpointTag)
                    ) {
                        continue
                    }
                    watcherScope.launch {
                        val title = formatTitle(
                            repository.getString(Res.string.tailscale_with_tag, notice.endpointTag),
                        )
                        val body = repository.getString(Res.string.tailscale_state_needs_login)
                        if (generation != contextGeneration || currentServerKey != serverKey) return@launch
                        if (!canNotify()) return@launch
                        if (notifications.isPageFocused() &&
                            currentRoute == RemoteRoute.Endpoint(notice.endpointTag)
                        ) {
                            return@launch
                        }
                        val snapshot = previousTailscaleSnapshots[notice.endpointTag]
                        if (snapshot == null || !snapshot.needsLogin || snapshot.authUrl.isEmpty()) {
                            return@launch
                        }
                        postNotification(
                            tag = tailscaleLoginTag(serverKey.id, notice.endpointTag),
                            title = title,
                            body = body,
                            onClick = {
                                clickActions.tryEmit(
                                    RemoteNotificationAction.OpenTailscaleEndpoint(notice.endpointTag),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun formatTitle(base: String): String {
        if (remote.servers.size <= 1) return base
        val serverName = remote.activeNativeServer.value?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: return base
        return "$base · $serverName"
    }

    private fun postNotification(
        tag: String,
        title: String,
        body: String,
        onClick: () -> Unit,
    ) {
        activeHandles.remove(tag)?.close()
        var registered: BrowserNotificationHandle? = null
        val handle = notifications.show(
            title = title,
            body = body,
            tag = tag,
            icon = NOTIFICATION_ICON,
            onClick = {
                if (activeHandles[tag] === registered) {
                    activeHandles.remove(tag)
                }
                onClick()
            },
            onClose = {
                if (activeHandles[tag] === registered) {
                    activeHandles.remove(tag)
                }
            },
        )
        if (handle != null) {
            registered = handle
            activeHandles[tag] = handle
        }
    }

    private fun closeTag(tag: String) {
        activeHandles.remove(tag)?.close()
    }

    private fun closeAll() {
        for (handle in activeHandles.values.toList()) {
            handle.close()
        }
        activeHandles.clear()
    }

    private fun openConnectTag(serverId: Long, endpointTag: String) =
        "$TAG_OPENCONNECT_PREFIX:$serverId:$endpointTag"

    private fun openVpnTag(serverId: Long, endpointTag: String) =
        "$TAG_OPENVPN_PREFIX:$serverId:$endpointTag"

    private fun tailscaleLoginPrefix(serverId: Long) =
        "$TAG_TAILSCALE_LOGIN_PREFIX:$serverId:"

    private fun tailscaleLoginTag(serverId: Long, endpointTag: String) =
        "${tailscaleLoginPrefix(serverId)}$endpointTag"

    private fun taildropPrefix(serverId: Long) =
        "$TAG_TAILDROP_PREFIX:$serverId:"

    private fun taildropTag(serverId: Long, endpointTag: String) =
        "${taildropPrefix(serverId)}$endpointTag"
}
