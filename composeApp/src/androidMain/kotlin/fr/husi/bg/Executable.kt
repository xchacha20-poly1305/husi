package fr.husi.bg

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.text.TextUtils
import fr.husi.ktx.Logs
import fr.husi.ktx.invariantPathString
import fr.husi.ktx.listOrEmpty
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.source
import java.io.IOException
import kotlinx.io.buffered
import kotlinx.io.readString

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
        for (process in PlatformFile("/proc").listOrEmpty().filter { TextUtils.isDigitsOnly(it.name) }) {
            val exe = PlatformFile(
                try {
                    (process / "cmdline").source().buffered().use {
                        it.readString()
                    }
                } catch (_: IOException) {
                    continue
                }.split(Character.MIN_VALUE, limit = 2).first(),
            )
            if (EXECUTABLES.contains(exe.name) || (alsoKillBg && exe.name.endsWith(":bg"))) try {
                Os.kill(process.name.toInt(), OsConstants.SIGKILL)
                Logs.w("SIGKILL ${exe.name} (${process.name}) succeed")
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.ESRCH) {
                    Logs.w("SIGKILL ${exe.invariantPathString()} (${process.name}) failed")
                    Logs.w(e)
                }
            }
        }
    }
}
