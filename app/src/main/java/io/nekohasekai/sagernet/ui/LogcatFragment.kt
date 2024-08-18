package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.os.FileObserver
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.databinding.ViewLogItemBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.utils.SendLog
import io.nekohasekai.sfa.utils.ColorUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import libcore.Libcore
import moe.matsuri.nb4a.utils.closeQuietly
import java.io.IOException
import java.io.RandomAccessFile
import java.util.LinkedList
import kotlin.coroutines.cancellation.CancellationException

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

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
        binding.logView.layoutManager = FixedLinearLayoutManager(binding.logView)
        binding.logView.adapter =
            LogAdapter(LinkedList(SendLog.logFile.readText().split(Libcore.LogSplitFlag))).also {
                logAdapter = it
            }
        lastPosition = SendLog.logFile.length()
        binding.logView.scrollToPosition(logAdapter.itemCount - 1)

        fileObserver.startWatching()
        freshJob = runOnDefaultDispatcher {
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

    inner class LogAdapter(val logList: LinkedList<String>) :
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

        fun notifyItemInserted(): Int {
            val position = logList.size - 1
            notifyItemInserted(position)
            return position
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
        try {
            while (true) {
                if (file.length() <= lastPosition) {
                    // Waiting for change
                    fileChange.receive()
                    continue
                }

                // Read new line and notify change
                file.seek(lastPosition)
                val remainingBytes = ByteArray((file.length() - lastPosition).toInt())
                file.readFully(remainingBytes)
                val lines = remainingBytes
                    .decodeToString()
                    .split(Libcore.LogSplitFlag)
                    .filterNot { it.isBlank() }
                if (lines.isNotEmpty()) {
                    logAdapter.logList.addAll(lines)
                    runOnMainDispatcher {
                        val position = logAdapter.notifyItemInserted()
                        if (!pinLog) binding.logView.scrollToPosition(position)
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
}