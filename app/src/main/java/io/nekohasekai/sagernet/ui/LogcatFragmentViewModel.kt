package io.nekohasekai.sagernet.ui

import android.os.Build
import android.os.FileObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.closeQuietly
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.utils.SendLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libcore.Libcore
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.cancellation.CancellationException

internal sealed class LogcatUpdateEvent {
    data class Appended(val newLogs: List<String>) : LogcatUpdateEvent()
    object Cleared : LogcatUpdateEvent()
    data class Error(val message: String) : LogcatUpdateEvent()
}

internal class LogcatFragmentViewModel : ViewModel() {
    companion object {
        private const val SPLIT_FLAG_LENGTH = Libcore.LogSplitFlag.length
    }

    private val logFile = SendLog.logFile

    private val logList = mutableListOf<String>()
    private val _updateEvents = MutableSharedFlow<LogcatUpdateEvent>()
    private val _pinLog = MutableStateFlow(false)
    private var lastPosition = 0L

    val currentLogs: List<String> get() = logList
    val updateEvents = _updateEvents.asSharedFlow()
    val pinLog = _pinLog.asStateFlow()

    private val fileChange = Channel<Unit>(Channel.CONFLATED)
    private val fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        object : FileObserver(logFile) {
            override fun onEvent(event: Int, path: String?) {
                if (event != MODIFY) return
                runBlocking { runCatching { fileChange.send(Unit) } }
            }
        }
    } else @Suppress("DEPRECATION")
    object : FileObserver(logFile.absolutePath) {
        override fun onEvent(event: Int, path: String?) {
            if (event != MODIFY) return
            runBlocking { runCatching { fileChange.send(Unit) } }
        }
    }

    init {
        loadAndObserveLog()
    }

    fun togglePinLog() {
        _pinLog.value = !_pinLog.value
    }

    fun clearLog() {
        viewModelScope.launch {
            try {
                logList.clear()
                lastPosition = 0L

                Libcore.logClear()
                Runtime.getRuntime().exec("/system/bin/logcat -c").waitFor()

                _updateEvents.emit(LogcatUpdateEvent.Cleared)
            } catch (e: Exception) {
                Logs.e(e)
                _updateEvents.emit(LogcatUpdateEvent.Error(e.readableMessage))
            }
        }
    }

    private fun loadAndObserveLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val initialLogs = logFile.inputStream().bufferedReader().use { reader ->
                val linesList = ArrayList<String>(256)
                while (true) {
                    val line = reader.readLogLine() ?: break
                    linesList.add(line)
                }
                linesList
            }
            logList.addAll(initialLogs)
            lastPosition = logFile.length()

            if (initialLogs.isNotEmpty()) {
                _updateEvents.emit(LogcatUpdateEvent.Appended(initialLogs))
            }

            fileObserver.startWatching()
            updateLogOnChange(RandomAccessFile(logFile, "r"))
        }
    }

    private suspend fun updateLogOnChange(file: RandomAccessFile) {
        try {
            while (true) {
                if (file.length() <= lastPosition) {
                    fileChange.receive()
                    continue
                }

                file.seek(lastPosition)
                val currentFileSize = file.length()
                val bytesToRead = (currentFileSize - lastPosition).toInt()
                val buffer = ByteArrayOutputStream(bytesToRead.coerceAtLeast(1024))
                val chunk = ByteArray(8 * 1024)
                var read: Int
                while (file.read(chunk).also { read = it } != -1) {
                    buffer.write(chunk, 0, read)
                    if (buffer.size() >= bytesToRead) break
                }

                val newBytes = buffer.toByteArray()
                if (newBytes.isNotEmpty()) {
                    val lines = newBytes.toString(Charsets.UTF_8)
                        .split(Libcore.LogSplitFlag)
                        .filterNot { it.isBlank() }

                    if (lines.isNotEmpty()) {
                        logList.addAll(lines)
                        _updateEvents.emit(LogcatUpdateEvent.Appended(lines))
                    }
                }
                lastPosition = file.filePointer
            }
        } catch (_: IOException) {
            // Coroutine scope cancelled or file closed
        } catch (_: CancellationException) {
            // Coroutine cancelled
        } catch (e: Exception) {
            Logs.w(e)
            _updateEvents.emit(LogcatUpdateEvent.Error(e.readableMessage))
        } finally {
            file.closeQuietly()
        }
    }

    private fun BufferedReader.readLogLine(): String? {
        val line = StringBuilder()
        while (true) {
            val charCode = this.read()
            if (charCode == -1) { // End of stream
                return if (line.isNotEmpty()) line.toString() else null
            }
            val char = charCode.toChar()
            line.append(char)
            if (line.endsWith(Libcore.LogSplitFlag)) {
                line.setLength(line.length - SPLIT_FLAG_LENGTH) // remove split flag
                return line.toString()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fileObserver.stopWatching()
        fileChange.close()
    }
}