package fr.husi.test

import fr.husi.bg.BrowserNotificationClient
import fr.husi.bg.BrowserNotificationHandle
import fr.husi.bg.BrowserNotificationPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeBrowserNotificationClient(
    initialPermission: BrowserNotificationPermission = BrowserNotificationPermission.GRANTED,
) : BrowserNotificationClient {

    data class ShownNotification(
        val title: String,
        val body: String,
        val tag: String,
        val icon: String,
        val onClick: () -> Unit,
        val onClose: () -> Unit,
        var closed: Boolean = false,
    )

    override val permission: StateFlow<BrowserNotificationPermission>
        field = MutableStateFlow(initialPermission)

    val shown = mutableListOf<ShownNotification>()
    val closedTags = mutableListOf<String>()
    var requestPermissionCount = 0
    var refreshPermissionCount = 0
    var closeCount = 0
    var pageFocused = false
    var nextRequestResult = BrowserNotificationPermission.GRANTED

    fun setPermission(value: BrowserNotificationPermission) {
        permission.value = value
    }

    fun activeNotifications(): List<ShownNotification> = shown.filter { !it.closed }

    fun activeTags(): List<String> = activeNotifications().map { it.tag }

    fun click(tag: String) {
        val notification = shown.lastOrNull { !it.closed && it.tag == tag }
            ?: error("No active notification with tag $tag")
        notification.closed = true
        closedTags += tag
        notification.onClick()
    }

    override fun refreshPermission() {
        refreshPermissionCount++
    }

    override fun requestPermission(onResult: (BrowserNotificationPermission) -> Unit) {
        requestPermissionCount++
        permission.value = nextRequestResult
        onResult(nextRequestResult)
    }

    override fun isPageFocused(): Boolean = pageFocused

    override fun show(
        title: String,
        body: String,
        tag: String,
        icon: String,
        onClick: () -> Unit,
        onClose: () -> Unit,
    ): BrowserNotificationHandle? {
        if (permission.value != BrowserNotificationPermission.GRANTED) return null
        for (existing in shown) {
            if (!existing.closed && existing.tag == tag) {
                existing.closed = true
                existing.onClose()
            }
        }
        val entry = ShownNotification(
            title = title,
            body = body,
            tag = tag,
            icon = icon,
            onClick = onClick,
            onClose = onClose,
        )
        shown += entry
        return BrowserNotificationHandle {
            if (!entry.closed) {
                entry.closed = true
                closedTags += tag
                entry.onClose()
            }
        }
    }

    override fun close() {
        closeCount++
        for (entry in shown) {
            entry.closed = true
        }
    }
}
