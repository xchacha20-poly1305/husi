package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutRuleSetMatchBinding
import io.nekohasekai.sagernet.databinding.ViewLogItemBinding
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ui.ThemedActivity
import libcore.Libcore

class RuleSetMatchActivity : ThemedActivity() {

    companion object {
        private const val KEY_RULE_SET_MATCHED = "rule_set_matched"
    }

    private lateinit var binding: LayoutRuleSetMatchBinding
    private lateinit var adapter: Adapter

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
                start(text.toString())
            }
        }
        binding.ruleSetMatchView.adapter = Adapter(mutableListOf()).also {
            adapter = it
        }

        if (savedInstanceState != null) {
            val matched = savedInstanceState.getStringArray(KEY_RULE_SET_MATCHED) ?: emptyArray()
            adapter.list.clear()
            adapter.list.addAll(matched)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray(KEY_RULE_SET_MATCHED, adapter.list.toTypedArray())
    }

    private fun start(keyword: String) {
        binding.startMatch.isEnabled = false
        adapter.list.clear()
        adapter.notifyDataSetChanged()
        runOnDefaultDispatcher {
            try {
                Libcore.scanRuleSet(keyword) {
                    runOnMainDispatcher {
                        adapter.list.add(it)
                        adapter.notifyLastChange()
                    }
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    binding.startMatch.isEnabled = true
                    MaterialAlertDialogBuilder(this@RuleSetMatchActivity)
                        .setTitle(R.string.error_title)
                        .setMessage(e.readableMessage)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                }
                return@runOnDefaultDispatcher
            }
            onMainDispatcher {
                binding.startMatch.isEnabled = true
            }
        }
    }

    inner class Adapter(val list: MutableList<String>) : RecyclerView.Adapter<Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ViewLogItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        fun notifyLastChange() {
            notifyItemInserted(list.size - 1)
        }
    }

    inner class Holder(val binding: ViewLogItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.text.text = text
        }
    }

}