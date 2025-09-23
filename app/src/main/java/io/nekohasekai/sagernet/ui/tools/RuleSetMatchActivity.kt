package io.nekohasekai.sagernet.ui.tools

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.databinding.LayoutToolsRuleSetMatchBinding
import io.nekohasekai.sagernet.databinding.ViewLogItemBinding
import io.nekohasekai.sagernet.ktx.alertAndLog
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.utils.SimpleDiffCallback
import kotlinx.coroutines.launch

class RuleSetMatchActivity : ThemedActivity() {

    private lateinit var binding: LayoutToolsRuleSetMatchBinding
    private val viewModel by viewModels<RuleSetMatchActivityViewModel>()
    private lateinit var adapter: RuleSetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutToolsRuleSetMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }

        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            Mdc3Theme {
                SimpleTopAppBar(
                    title = R.string.rule_set_match,
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                ) {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        binding.startMatch.setOnClickListener {
            val text = binding.ruleSetKeyword.text
            if (text.isNullOrBlank()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.group_status_empty)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            } else {
                viewModel.scan(text.toString())
            }
        }

        binding.ruleSetMatchView.adapter = RuleSetAdapter().also {
            adapter = it
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    private fun handleUiState(state: RuleSetMatchUiState) {
        when (state) {
            RuleSetMatchUiState.Idle -> {
                binding.startMatch.isEnabled = true
            }

            is RuleSetMatchUiState.Doing -> {
                binding.startMatch.isEnabled = false
                adapter.submitList(state.matched)
            }

            is RuleSetMatchUiState.Done -> {
                binding.startMatch.isEnabled = true
                adapter.submitList(state.matched)

                state.exception?.let { e ->
                    alertAndLog(e)
                }
            }
        }
    }

    private class RuleSetAdapter : ListAdapter<String, Holder>(SimpleDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ViewLogItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class Holder(val binding: ViewLogItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.text.text = text

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