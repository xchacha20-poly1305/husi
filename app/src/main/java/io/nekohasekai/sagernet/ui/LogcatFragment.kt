package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.os.FileObserver
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.databinding.ViewLogItemBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.closeQuietly
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.utils.SendLog
import io.nekohasekai.sfa.utils.ColorUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import libcore.Libcore
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.cancellation.CancellationException

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    companion object {
        private const val SPLIT_FLAG_LENGTH = Libcore.LogSplitFlag.length
    }

    lateinit var binding: LayoutLogcatBinding
    private lateinit var logAdapter: LogAdapter
    private var freshJob: Job? = null
    private var fileChange = Channel<Unit>()

    @Suppress("DEPRECATION") // FileObserver(File) require API 29
    private val fileObserver = object : FileObserver(SendLog.logFile.absolutePath) {
        override fun onEvent(event: Int, path: String?) {
            if (event != MODIFY) return
            runBlocking {
                runCatching { fileChange.send(Unit) }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)
        binding = LayoutLogcatBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.logView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(8),
                right = bars.right + dp2px(8),
                bottom = bars.bottom + dp2px(64),
            )
            WindowInsetsCompat.CONSUMED
        }

        logAdapter = LogAdapter(
            SendLog.logFile
                .inputStream()
                .bufferedReader()
                .use { reader ->
                    // We just add new item on the tail of list,
                    // and in many times array list has a better performance than linked list.
                    val linesList = ArrayList<String>(64)
                    while (true) {
                        val line = reader.readLogLine() ?: break
                        linesList.add(line)
                    }
                    linesList
                }
        )
        binding.logView.adapter = logAdapter

        lastPosition = SendLog.logFile.length()
        binding.logView.scrollToPosition(logAdapter.itemCount - 1)

        fileObserver.startWatching()
        freshJob = runOnIoDispatcher {
            updateLog(RandomAccessFile(SendLog.logFile, "r"))
        }
    }

    private var pinLog = false

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_pin_logcat -> {
                item.isChecked = if (pinLog) {
                    item.setIcon(R.drawable.ic_baseline_push_pin_24)
                    pinLog = false
                    false
                } else {
                    item.setIcon(R.drawable.ic_maps_360)
                    pinLog = true
                    true
                }
            }

            R.id.action_clear_logcat -> {
                lastPosition = 0L
                logAdapter.logList.clear()
                logAdapter.notifyDataSetChanged()

                runOnDefaultDispatcher {
                    try {
                        Libcore.logClear()
                        Runtime.getRuntime().exec("/system/bin/logcat -c")
                    } catch (e: Exception) {
                        Logs.e(e) // ?
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }

            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "husi")
                }
            }
        }

        return true
    }

    override fun onDestroyView() {
        fileObserver.stopWatching()
        freshJob?.cancel()
        fileChange.close()
        super.onDestroyView()
    }

    inner class LogAdapter(val logList: MutableList<String>) :
        RecyclerView.Adapter<LogViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            return LogViewHolder(
                ViewLogItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logList[position])
        }

        override fun getItemCount(): Int {
            return logList.size
        }
    }

    inner class LogViewHolder(val binding: ViewLogItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: String) {
            binding.text.text = ColorUtils.ansiEscapeToSpannable(binding.root.context, message)
        }
    }

    // last position for log file
    private var lastPosition = 0L

    private suspend fun updateLog(file: RandomAccessFile) {
        val sharedBuffer = ByteArrayOutputStream(256) // Initialize with a reasonable size

        try {
            while (true) {
                if (file.length() <= lastPosition) {
                    // Wait for change
                    fileChange.receive()
                    continue
                }

                // Read new line and notify change
                file.seek(lastPosition)
                val currentFileSize = file.length()
                val bytesToRead = (currentFileSize - lastPosition).toInt()

                sharedBuffer.reset() // Clear buffer before reading new data

                val buffer = ByteArray(
                    minOf(bytesToRead, 8192)
                ) // Read in chunks to avoid OOM for very large changes
                var totalBytesRead = 0
                while (totalBytesRead < bytesToRead) {
                    val readLength = minOf(buffer.size, bytesToRead - totalBytesRead)
                    val bytesRead = file.read(buffer, 0, readLength)
                    if (bytesRead == -1) break // EOF reached unexpectedly
                    if (bytesRead > 0) {
                        sharedBuffer.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    } else {
                        break // No bytes read
                    }
                }


                if (sharedBuffer.size() > 0) {
                    val lines = sharedBuffer.toString()
                        .split(Libcore.LogSplitFlag)
                        .filterNot { it.isBlank() }

                    if (lines.isNotEmpty()) {
                        val startPosition = logAdapter.logList.size
                        logAdapter.logList.addAll(lines)
                        onMainDispatcher {
                            logAdapter.notifyItemRangeInserted(startPosition, lines.size)

                            // Do not use ktx.scrollTo().
                            // Because if the page is long, that will move very slow and
                            // make very strange animation.
                            if (!pinLog) binding.logView.scrollToPosition(logAdapter.itemCount - 1)
                        }
                    }
                }
                lastPosition = file.filePointer
            }
        } catch (_: IOException) {
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            Logs.w(e)
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
}
