package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.FileObserver
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
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
import libcore.Libcore
import moe.matsuri.nb4a.utils.setOnFocusCancel
import java.util.concurrent.locks.ReentrantLock

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener,
    SearchView.OnQueryTextListener {

    lateinit var binding: LayoutLogcatBinding
    private lateinit var logAdapter: LogAdapter
    private val lock = ReentrantLock()

    @Suppress("DEPRECATION") // FileObserver(File) require API 29
    private val fileObserver = object : FileObserver(SendLog.logFile.absolutePath) {
        override fun onEvent(event: Int, path: String?) {
            if (event != MODIFY) return
            runOnMainDispatcher {
                lock.lock()
                logAdapter.logList = getLogList()
                if (searchKeyword.isNullOrBlank()) {
                    // May be just update one?
                    binding.logView.scrollToPosition(logAdapter.notifyItemInserted())
                }
                lock.unlock()
            }
        }
    }

    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)
        binding.logView.layoutManager = FixedLinearLayoutManager(binding.logView)
        binding.logView.adapter = LogAdapter(getLogList()).also {
            logAdapter = it
        }
        binding.logView.scrollToPosition(logAdapter.itemCount - 1)

        searchView = toolbar.findViewById<SearchView>(R.id.action_log_search).apply {
            setOnQueryTextListener(this@LogcatFragment)
            setOnFocusCancel()
        }

        binding.fabUp.setOnClickListener {
            if (searchIndex <= 0) {
                searchIndex = matched.size - 1 // loop back
            } else {
                searchIndex--
            }
            binding.logView.scrollToPosition(matched[searchIndex])
        }
        binding.fabDown.setOnClickListener {
            if (searchIndex >= matched.size - 1) {
                searchIndex = 0 // loop back
            } else {
                searchIndex++
            }
            binding.logView.scrollToPosition(matched[searchIndex])
        }

        fileObserver.startWatching()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_logcat -> {
                searchKeyword = null
                matched.clear()
                searchIndex = 0
                setFabVisibility(false)

                lock.lock()
                logAdapter.logList = listOf()
                logAdapter.notifyDataSetChanged()
                lock.unlock()

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
        super.onDestroyView()
    }

    private fun getLogList(): List<String> {
        return String(SendLog.getCoreLog(50 * 1024)).split("\n")
    }

    private lateinit var searchView: SearchView
    private var searchKeyword: String? = null
    private var matched = mutableListOf<Int>()
    private var searchIndex = 0

    override fun onQueryTextSubmit(query: String?): Boolean {
        searchKeyword = query

        matched.clear()
        searchIndex = 0

        if (!query.isNullOrBlank()) {
            lock.lock()

            // FIXME
            for ((i, logText) in logAdapter.logList.withIndex()) {
                if (!logText.contains(searchKeyword!!, true)) continue
                matched.add(i)
                val item = binding.logView.findViewHolderForAdapterPosition(i) ?: continue
                item as LogViewHolder
                val originText = item.binding.text.text
                item.binding.text.text = ColorUtils.highlightKeyword(
                    originText.toSpannable(),
                    searchKeyword!!,
                    ContextCompat.getColor(binding.root.context, R.color.material_amber_400),
                )
            }

            if (matched.isNotEmpty()) {
                setFabVisibility(true)
                binding.logView.scrollToPosition(matched[0])
            } else {
                setFabVisibility(false)
            }

            lock.unlock()
        } else {
            setFabVisibility(false)
        }

        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        return false
    }


    inner class LogAdapter(var logList: List<String>) :
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

    private fun setFabVisibility(visibility: Boolean) {
        binding.fabUp.isVisible = visibility
        binding.fabDown.isVisible = visibility
    }
}