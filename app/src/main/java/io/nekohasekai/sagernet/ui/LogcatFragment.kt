package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.databinding.ViewLogItemBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.utils.SendLog
import io.nekohasekai.sfa.utils.ColorUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class LogcatFragment : OnKeyDownFragment(R.layout.layout_logcat) {

    private lateinit var binding: LayoutLogcatBinding
    private val viewModel: LogcatFragmentViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutLogcatBinding.bind(view)
        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                val isPinned by viewModel.pinLog.collectAsStateWithLifecycle()
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_log)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.menu),
                        ) {
                            (requireActivity() as MainActivity).binding
                                .drawerLayout.openDrawer(GravityCompat.START)
                        }
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = if (isPinned) {
                                Icons.Filled.Sailing
                            } else {
                                Icons.Filled.PushPin
                            },
                            contentDescription = stringResource(R.string.pin_log),
                            onClick = { viewModel.togglePinLog() },
                        )
                        SimpleIconButton(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.logcat),
                        ) {
                            try {
                                SendLog.sendLog(requireContext(), "husi")
                            } catch (e: Exception) {
                                lifecycleScope.launch {
                                    Logs.e(e)
                                }
                                snackbar(e.readableMessage).show()
                            }
                        }
                        SimpleIconButton(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_logcat),
                            onClick = { viewModel.clearLog() },
                        )
                    },
                )
            }
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

        logAdapter = LogAdapter(ArrayList(128))
        binding.logView.adapter = logAdapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleEvents)
            }
        }

        if (savedInstanceState == null) {
            viewModel.initialize()
        }
    }

    private fun handleEvents(event: LogcatUiEvent) {
        when (event) {
            is LogcatUiEvent.Appended -> {
                val currentSize = logAdapter.itemCount
                logAdapter.appendLogs(event.newLogs)
                logAdapter.notifyItemRangeInserted(currentSize, event.newLogs.size)
                if (!viewModel.pinLog.value) {
                    scrollToBottom()
                }
            }

            is LogcatUiEvent.Cleared -> {
                logAdapter.clearLogs()
                logAdapter.notifyDataSetChanged()
            }

            is LogcatUiEvent.Error -> {
                snackbar(event.message).show()
            }
        }
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

            // Make ripple even text selectable.
            val gestureDetector = GestureDetector(
                binding.root.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        binding.root.performClick()
                        return true
                    }
                },
            )
            @SuppressLint("ClickableViewAccessibility") binding.text.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.root.isPressed = true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        binding.root.isPressed = false
                    }
                }
                false
            }
        }
    }

}
