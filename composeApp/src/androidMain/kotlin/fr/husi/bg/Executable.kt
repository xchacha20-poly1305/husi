package fr.husi.bg

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.text.TextUtils
import fr.husi.ktx.Logs
import fr.husi.ktx.inputStream
import fr.husi.ktx.listFilesOrNull
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.name
import java.io.IOException
import androidx.core.text.isDigitsOnly

actual object Executable {
    private val EXECUTABLES = setOf(
        "libtrojan.so",
        "libtrojan-go.so",
        "libnaive.so",
        "libhysteria.so",
        "libhysteria2.so",
        "libjuicity.so",
        "libshadowquic.so",
    )

    actual fun killAll(alsoKillBg: Boolean) {
        for (process in PlatformFile("/proc").listFilesOrNull { it.name.isDigitsOnly() }
            ?: return) {
            val rawCommand = try {
                PlatformFile(process, "cmdline").inputStream().bufferedReader().use {
                    it.readText()
                }
            } catch (_: IOException) {
                continue
            }
            val exe = PlatformFile(rawCommand.split(Character.MIN_VALUE, limit = 2).first())
            if (EXECUTABLES.contains(exe.name) || (alsoKillBg && exe.name.endsWith(":bg"))) {
                try {
                    Os.kill(process.name.toInt(), OsConstants.SIGKILL)
                    Logs.w("SIGKILL ${exe.name} (${process.name}) succeed")
                } catch (e: ErrnoException) {
                    if (e.errno != OsConstants.ESRCH) {
                        Logs.w("SIGKILL ${exe.absolutePath()} (${process.name}) failed")
                        Logs.w(e)
                    }
                }
            }
        }
    }
}
