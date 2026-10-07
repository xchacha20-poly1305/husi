package fr.husi.cli

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import fr.husi.platform.PlatformInfo

/** The three standard streams: their POSIX descriptor and their Windows `GetStdHandle` id. */
internal enum class StandardStream(val unixDescriptor: Int, val windowsHandleId: Int) {
    Input(unixDescriptor = 0, windowsHandleId = -10),
    Output(unixDescriptor = 1, windowsHandleId = -11),
    Error(unixDescriptor = 2, windowsHandleId = -12),
}

@Suppress("FunctionName")
private interface CLibrary : Library {
    fun isatty(fd: Int): Int
}

@Suppress("FunctionName")
private interface Kernel32Library : Library {
    fun GetStdHandle(nStdHandle: Int): Pointer?
    fun GetConsoleMode(hConsoleHandle: Pointer, lpMode: IntByReference): Int
}

/**
 * Whether [stream] is attached to a terminal. [java.io.Console.isTerminal] cannot tell the
 * streams apart, and only exists on Java 22+.
 */
internal fun isTerminal(stream: StandardStream): Boolean = try {
    if (PlatformInfo.isWindows) {
        val kernel32 = Native.load("kernel32", Kernel32Library::class.java)
        val handle = kernel32.GetStdHandle(stream.windowsHandleId)
        handle != null && kernel32.GetConsoleMode(handle, IntByReference()) != 0
    } else {
        Native.load("c", CLibrary::class.java).isatty(stream.unixDescriptor) == 1
    }
} catch (_: UnsatisfiedLinkError) {
    false
}
