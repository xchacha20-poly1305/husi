package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.databinding.ViewLogItemBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.utils.SendLog
import io.nekohasekai.sfa.utils.ColorUtils
import kotlinx.coroutines.launch

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    private lateinit var binding: LayoutLogcatBinding
    private val viewModel: LogcatFragmentViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

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
            insets
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
            insets
        }

        logAdapter = LogAdapter(viewModel.currentLogs.toMutableList())
        binding.logView.adapter = logAdapter

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.updateEvents.collect { event ->
                        when (event) {
                            is LogcatUpdateEvent.Appended -> {
                                val currentSize = logAdapter.itemCount
                                logAdapter.appendLogs(event.newLogs)
                                logAdapter.notifyItemRangeInserted(currentSize, event.newLogs.size)
                                if (!viewModel.pinLog.value) {
                                    scrollToBottom()
                                }
                            }

                            is LogcatUpdateEvent.Cleared -> {
                                logAdapter.clearLogs()
                                logAdapter.notifyDataSetChanged()
                            }

                            is LogcatUpdateEvent.Error -> {
                                snackbar(event.message).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.pinLog.collect { isPinned ->
                        val pinMenuItem = toolbar.menu.findItem(R.id.action_pin_logcat)
                        if (isPinned) {
                            pinMenuItem?.setIcon(R.drawable.ic_maps_360)
                            pinMenuItem?.isChecked = true
                        } else {
                            pinMenuItem?.setIcon(R.drawable.ic_baseline_push_pin_24)
                            pinMenuItem?.isChecked = false
                        }
                    }
                }
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_pin_logcat -> {
            viewModel.togglePinLog()
            true
        }

        R.id.action_clear_logcat -> {
            viewModel.clearLog()
            true
        }

        R.id.action_send_logcat -> {
            lifecycleScope.launch {
                try {
                    SendLog.sendLog(requireContext(), "husi")
                } catch (e: Exception) {
                    Logs.e(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }
            }
            true
        }

        else -> false
    }

    private fun scrollToBottom() {
        val itemCount = logAdapter.itemCount
        if (itemCount > 0) {
            binding.logView.scrollToPosition(itemCount - 1)
        }
    }

    private class LogAdapter(private val logList: MutableList<String>) :
        RecyclerView.Adapter<LogViewHolder>() {

        fun appendLogs(newLogs: List<String>) {
            logList.addAll(newLogs)
        }

        fun clearLogs() {
            logList.clear()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            return LogViewHolder(
                ViewLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logList[position])
        }

        override fun getItemCount(): Int = logList.size
    }

    private class LogViewHolder(private val binding: ViewLogItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: String) {
            binding.text.text = ColorUtils.ansiEscapeToSpannable(binding.root.context, message)
        }
    }

}
