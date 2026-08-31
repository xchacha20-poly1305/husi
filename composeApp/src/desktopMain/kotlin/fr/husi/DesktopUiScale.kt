package fr.husi

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import fr.husi.ktx.Logs
import fr.husi.platform.PlatformInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val X11_LIBRARY_NAME = "X11"
private const val DISPLAY_ENVIRONMENT_VARIABLE = "DISPLAY"
private const val XFT_DPI_RESOURCE = "Xft.dpi"
private const val UNSCALED_DPI = 96f

private val X_RESOURCE_WAIT_TIMEOUT = 5.seconds
private val X_RESOURCE_POLL_INTERVAL = 100.milliseconds

@Suppress("FunctionName")
private interface X11Library : Library {
    fun XOpenDisplay(displayName: String?): Pointer?
    fun XResourceManagerString(display: Pointer): String?
    fun XCloseDisplay(display: Pointer): Int
}

sealed interface LinuxUiScaleWait {
    data object Skipped : LinuxUiScaleWait
    data class X11Unavailable(val error: UnsatisfiedLinkError) : LinuxUiScaleWait
    data class Published(val scale: Float?, val waited: Duration) : LinuxUiScaleWait
    data object TimedOut : LinuxUiScaleWait
}

/**
 * Wait until x11 available to fix scale not right.
 */
fun awaitLinuxUiScaleSettings(): LinuxUiScaleWait {
    if (!PlatformInfo.isLinux) return LinuxUiScaleWait.Skipped
    if (System.getenv(DISPLAY_ENVIRONMENT_VARIABLE).isNullOrBlank()) return LinuxUiScaleWait.Skipped

    val x11 = try {
        Native.load(X11_LIBRARY_NAME, X11Library::class.java)
    } catch (e: UnsatisfiedLinkError) {
        return LinuxUiScaleWait.X11Unavailable(e)
    }

    val startedAtNanos = System.nanoTime()
    val giveUpAtNanos = startedAtNanos + X_RESOURCE_WAIT_TIMEOUT.inWholeNanoseconds
    while (true) {
        val resourceDatabase = x11.readResourceDatabase() ?: return LinuxUiScaleWait.Skipped
        if (isXResourceDatabasePublished(resourceDatabase)) {
            return LinuxUiScaleWait.Published(
                scale = parseXftDpiScale(resourceDatabase),
                waited = (System.nanoTime() - startedAtNanos).nanoseconds,
            )
        }
        if (System.nanoTime() >= giveUpAtNanos) return LinuxUiScaleWait.TimedOut
        Thread.sleep(X_RESOURCE_POLL_INTERVAL.inWholeMilliseconds)
    }
}

fun LinuxUiScaleWait.logOutcome() {
    when (this) {
        LinuxUiScaleWait.Skipped -> Unit

        is LinuxUiScaleWait.X11Unavailable -> Logs.d(
            "lib$X11_LIBRARY_NAME is unavailable, did not wait for the UI scale settings",
            error,
        )

        is LinuxUiScaleWait.Published -> Logs.i(
            "X resource database published after ${waited.inWholeMilliseconds} ms, " +
                "$XFT_DPI_RESOURCE scale ${scale ?: "unset"}",
        )

        LinuxUiScaleWait.TimedOut -> Logs.w(
            "X resource database still empty after $X_RESOURCE_WAIT_TIMEOUT, " +
                "the window may render unscaled",
        )
    }
}

internal fun isXResourceDatabasePublished(resourceDatabase: String): Boolean {
    return resourceDatabase.isNotBlank()
}

internal fun parseXftDpiScale(resourceDatabase: String): Float? {
    for (line in resourceDatabase.lineSequence()) {
        val separator = line.indexOf(':')
        if (separator < 0) continue
        if (line.substring(0, separator).trim() != XFT_DPI_RESOURCE) continue

        val dpi = line.substring(separator + 1).trim().toFloatOrNull() ?: return null
        if (dpi <= 0f) return null
        return dpi / UNSCALED_DPI
    }
    return null
}

private fun X11Library.readResourceDatabase(): String? {
    val display = XOpenDisplay(null) ?: return null
    return try {
        XResourceManagerString(display).orEmpty()
    } finally {
        XCloseDisplay(display)
    }
}
