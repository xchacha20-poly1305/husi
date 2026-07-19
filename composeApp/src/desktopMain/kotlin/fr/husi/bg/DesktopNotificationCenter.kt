package fr.husi.bg

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import fr.husi.ktx.Logs
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.app_name
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.AWTException
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.Timer
import kotlin.concurrent.schedule

object DesktopNotificationCenter {

    suspend fun show(title: String, message: String) {
        runCatching {
            val appName = resolveRepository().getString(Res.string.app_name)
            when {
                PlatformInfo.isLinux -> LinuxNotificationProvider.show(appName, title, message)
                PlatformInfo.isMacOs -> MacOsNotificationProvider.show(title, message)
                PlatformInfo.isWindows -> WindowsNotificationProvider.show(appName, title, message)
            }
        }.onFailure {
            Logs.w("show desktop notification", it)
        }
    }
}

@DBusInterfaceName("org.freedesktop.Notifications")
private interface FreedesktopNotifications : DBusInterface {
    @Suppress("FunctionName")
    fun Notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int,
    ): UInt32
}

private object LinuxNotificationProvider {

    fun show(appName: String, title: String, message: String) {
        DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
            val notifications = connection.getRemoteObject(
                "org.freedesktop.Notifications",
                "/org/freedesktop/Notifications",
                FreedesktopNotifications::class.java,
            )
            notifications.Notify(
                appName = appName,
                replacesId = UInt32(0),
                appIcon = "",
                summary = title,
                body = message,
                actions = emptyList(),
                hints = emptyMap(),
                expireTimeout = -1,
            )
        }
    }
}

private object MacOsNotificationProvider {

    private val objectiveCLibrary by lazy {
        NativeLibrary.getInstance("Foundation")
        NativeLibrary.getInstance("objc")
    }
    private val getClassFunction by lazy { objectiveCLibrary.getFunction("objc_getClass") }
    private val registerSelectorFunction by lazy { objectiveCLibrary.getFunction("sel_registerName") }
    private val sendMessageFunction by lazy { objectiveCLibrary.getFunction("objc_msgSend") }

    fun show(title: String, message: String) {
        val pool = sendPointer(getClass("NSAutoreleasePool"), "new")
        try {
            val notification = sendPointer(
                sendPointer(getClass("NSUserNotification"), "alloc"),
                "init",
            )
            sendVoid(notification, "setTitle:", nsString(title))
            sendVoid(notification, "setInformativeText:", nsString(message))
            val center = sendPointer(getClass("NSUserNotificationCenter"), "defaultUserNotificationCenter")
            sendVoid(center, "deliverNotification:", notification)
            sendVoid(notification, "release")
        } finally {
            sendVoid(pool, "drain")
        }
    }

    private fun nsString(value: String): Pointer =
        sendPointer(getClass("NSString"), "stringWithUTF8String:", value)

    private fun getClass(name: String): Pointer =
        getClassFunction.invokePointer(arrayOf(name)) ?: error("Objective-C class not found: $name")

    private fun selector(name: String): Pointer =
        registerSelectorFunction.invokePointer(arrayOf(name)) ?: error("Objective-C selector not found: $name")

    private fun sendPointer(receiver: Pointer, selector: String, vararg arguments: Any): Pointer =
        sendMessageFunction.invokePointer(arrayOf(receiver, selector(selector), *arguments))
            ?: error("Objective-C message returned null: $selector")

    private fun sendVoid(receiver: Pointer, selector: String, vararg arguments: Any) {
        sendMessageFunction.invokeVoid(arrayOf(receiver, selector(selector), *arguments))
    }
}

private object WindowsNotificationProvider {

    private const val NOTIFICATION_DURATION_MILLIS = 10_000L
    private val cleanupTimer = Timer("desktop-notification-cleanup", true)

    fun show(appName: String, title: String, message: String) {
        check(SystemTray.isSupported()) { "System tray notifications are not supported" }
        val tray = SystemTray.getSystemTray()
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val trayIcon = TrayIcon(image, appName)
        try {
            tray.add(trayIcon)
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO)
        } catch (exception: AWTException) {
            throw IllegalStateException("Unable to add the notification tray icon", exception)
        }
        cleanupTimer.schedule(NOTIFICATION_DURATION_MILLIS) {
            tray.remove(trayIcon)
        }
    }
}
