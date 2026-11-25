@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.bg

import fr.husi.ktx.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.js.JsAny

internal enum class BrowserNotificationPermission {
    UNSUPPORTED,
    DEFAULT,
    GRANTED,
    DENIED,
}

internal fun interface BrowserNotificationHandle {
    fun close()
}

internal interface BrowserNotificationClient {
    val permission: StateFlow<BrowserNotificationPermission>

    fun refreshPermission()

    /** Initiates Notification.requestPermission() synchronously to preserve browser user activation. */
    fun requestPermission(onResult: (BrowserNotificationPermission) -> Unit)

    fun isPageFocused(): Boolean

    fun show(
        title: String,
        body: String,
        tag: String,
        icon: String,
        onClick: () -> Unit,
        onClose: () -> Unit,
    ): BrowserNotificationHandle?

    fun close()
}

internal class WebBrowserNotificationClient : BrowserNotificationClient {
    override val permission: StateFlow<BrowserNotificationPermission>
        field = MutableStateFlow(parsePermission(jsNotificationPermission()))

    private val openHandles = mutableSetOf<BrowserNotificationHandle>()
    private var listenerSubscription: ListenerSubscription? = null

    init {
        listenerSubscription = registerEnvironmentListeners(
            onRefresh = ::refreshPermission,
            onPageHide = ::closeOpenNotifications,
        )
    }

    override fun refreshPermission() {
        permission.value = parsePermission(jsNotificationPermission())
    }

    override fun requestPermission(onResult: (BrowserNotificationPermission) -> Unit) {
        if (permission.value == BrowserNotificationPermission.UNSUPPORTED) {
            onResult(BrowserNotificationPermission.UNSUPPORTED)
            return
        }
        jsRequestNotificationPermission { raw ->
            val resolved = parsePermission(raw)
            permission.value = resolved
            onResult(resolved)
        }
    }

    override fun isPageFocused(): Boolean = jsIsPageFocused()

    override fun show(
        title: String,
        body: String,
        tag: String,
        icon: String,
        onClick: () -> Unit,
        onClose: () -> Unit,
    ): BrowserNotificationHandle? {
        if (permission.value != BrowserNotificationPermission.GRANTED) return null
        return runCatching {
            lateinit var handle: BrowserNotificationHandle
            val jsHandle = jsShowNotification(
                title = title,
                body = body,
                tag = tag,
                icon = icon,
                onClick = {
                    openHandles.remove(handle)
                    onClick()
                },
                onClose = {
                    openHandles.remove(handle)
                    onClose()
                },
            )
            handle = BrowserNotificationHandle {
                openHandles.remove(handle)
                runCatching { jsHandle.close() }
            }
            openHandles.add(handle)
            handle
        }.onFailure { failure ->
            Logs.w("Failed to show browser notification", failure)
        }.getOrNull()
    }

    override fun close() {
        listenerSubscription?.close()
        listenerSubscription = null
        closeOpenNotifications()
    }

    private fun closeOpenNotifications() {
        for (handle in openHandles.toList()) {
            handle.close()
        }
        openHandles.clear()
    }

    private companion object {
        fun parsePermission(raw: String): BrowserNotificationPermission = when (raw) {
            "granted" -> BrowserNotificationPermission.GRANTED
            "denied" -> BrowserNotificationPermission.DENIED
            "default" -> BrowserNotificationPermission.DEFAULT
            else -> BrowserNotificationPermission.UNSUPPORTED
        }
    }
}

private external interface JsNotificationHandle : JsAny {
    fun close()
}

private external interface ListenerSubscription : JsAny {
    fun close()
}

@JsFun("""
    () => {
        if (typeof Notification === "undefined" || !globalThis.isSecureContext) {
            return "unsupported";
        }
        return String(Notification.permission);
    }
""")
private external fun jsNotificationPermission(): String

@JsFun("""
    (onResult) => {
        try {
            const result = Notification.requestPermission();
            if (result && typeof result.then === "function") {
                result.then(
                    (value) => onResult(String(value)),
                    () => onResult(String(Notification.permission)),
                );
            } else {
                onResult(String(result ?? Notification.permission));
            }
        } catch (_) {
            onResult("denied");
        }
    }
""")
private external fun jsRequestNotificationPermission(onResult: (String) -> Unit)

@JsFun("""() => document.visibilityState === "visible" && document.hasFocus()""")
private external fun jsIsPageFocused(): Boolean

@JsFun("""
    (title, body, tag, icon, onClick, onClose) => {
        const notification = new Notification(title, { body, tag, icon });
        notification.onclick = () => {
            try {
                window.focus();
            } catch (_) {}
            notification.close();
            onClick();
        };
        notification.onclose = () => {
            onClose();
        };
        return {
            close: () => {
                notification.onclose = null;
                notification.onclick = null;
                notification.close();
            }
        };
    }
""")
private external fun jsShowNotification(
    title: String,
    body: String,
    tag: String,
    icon: String,
    onClick: () -> Unit,
    onClose: () -> Unit,
): JsNotificationHandle

@JsFun("""
    (onRefresh, onPageHide) => {
        window.addEventListener("focus", onRefresh);
        document.addEventListener("visibilitychange", onRefresh);
        window.addEventListener("pagehide", onPageHide);
        return {
            close: () => {
                window.removeEventListener("focus", onRefresh);
                document.removeEventListener("visibilitychange", onRefresh);
                window.removeEventListener("pagehide", onPageHide);
            }
        };
    }
""")
private external fun registerEnvironmentListeners(
    onRefresh: () -> Unit,
    onPageHide: () -> Unit,
): ListenerSubscription
