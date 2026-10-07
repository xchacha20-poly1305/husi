package fr.husi.ktx

import fr.husi.proto.daemon.LogLevel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class LogFile(private val file: File, private val level: LogLevel, truncate: Boolean) {

    private val lock = Any()
    private val output: FileOutputStream? = try {
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).also { if (truncate) it.channel.truncate(0) }
    } catch (e: IOException) {
        System.err.println("open log file $file: ${e.message}")
        null
    }

    fun write(level: LogLevel, tag: String, message: String) {
        if (level.number > this.level.number) return
        val line = "${level.name} [$tag] $message\n"
        synchronized(lock) {
            try {
                output?.write(line.encodeToByteArray())
            } catch (_: IOException) {
                // Reporting a failed log write through the log would recurse.
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            try {
                output?.channel?.truncate(0)
            } catch (e: IOException) {
                System.err.println("clear log file $file: ${e.message}")
            }
        }
    }

    fun close() {
        synchronized(lock) {
            output?.close()
        }
    }
}
