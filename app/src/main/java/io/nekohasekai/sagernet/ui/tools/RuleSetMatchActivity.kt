package io.nekohasekai.sagernet.ui.tools

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutRuleSetMatchBinding
import io.nekohasekai.sagernet.databinding.ViewLogItemBinding
import io.nekohasekai.sagernet.ktx.alertAndLog
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.utils.SimpleDiffCallback

class RuleSetMatchActivity : ThemedActivity() {

    private lateinit var binding: LayoutRuleSetMatchBinding
    private val viewModel: RuleSetMatchActivityViewModel by viewModels()
    private lateinit var adapter: RuleSetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutRuleSetMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
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

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.rule_set_match)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
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

        viewModel.uiState.observe(this, ::handleUiState)
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