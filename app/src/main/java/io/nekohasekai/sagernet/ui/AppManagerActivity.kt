package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppsBinding
import io.nekohasekai.sagernet.databinding.LayoutLoadingBinding
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.SimpleDiffCallback

class AppManagerActivity : AbstractAppListActivity() {

    override val viewModel by viewModels<AppManagerActivityViewModel>()
    private lateinit var binding: LayoutAppsBinding
    override val root get() = binding.root
    override val toolbarTitle get() = R.string.proxied_apps
    override val toolbar get() = binding.toolbar
    override val collapsingToolbar get() = binding.collapsingToolbar
    override val search get() = binding.search
    override val showSystemApps get() = binding.showSystemApps
    override val loading get() = binding.loading
    override val list get() = binding.list
    override val snackbarAnchor get() = binding.list

    override fun bind(layoutInflater: LayoutInflater) {
        binding = LayoutAppsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DataStore.proxyApps) {
            DataStore.proxyApps = true
        }

        binding.bypassGroup.check(
            if (DataStore.bypassMode) {
                R.id.appProxyModeBypass
            } else {
                R.id.appProxyModeOn
            }
        )
        binding.bypassGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.appProxyModeDisable -> {
                    DataStore.proxyApps = false
                    finish()
                }

                R.id.appProxyModeOn -> DataStore.bypassMode = false
                R.id.appProxyModeBypass -> DataStore.bypassMode = true
            }
        }
    }

    private var scanDialog: AlertDialog? = null
    private var scanBinding: LayoutLoadingBinding? = null
    private var scanText: TextViewAdapter? = null

    override fun handleUiState(state: AbstractAppListUiState) {
        super.handleUiState(state)
        state as AppManagerUiState
        if (state.scanned == null) {
            scanDialog?.dismiss()
            scanText?.submitList(null) {
                scanDialog = null
                scanBinding = null
                scanText = null
            }
        } else {
            if (scanDialog == null) {
                scanBinding = LayoutLoadingBinding.inflate(layoutInflater)
                scanBinding!!.recyclerView.adapter = TextViewAdapter().also {
                    scanText = it
                }
                scanDialog = MaterialAlertDialogBuilder(this)
                    .setView(scanBinding!!.root)
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        viewModel.cancelScan()
                    }
                    .setCancelable(true)
                    .setOnCancelListener {
                        viewModel.cancelScan()
                    }
                    .show()
            }
            scanText!!.submitList(state.scanned) {
                scanBinding?.process?.setProgressCompat(state.scanProcess!!, true)
                val scrollTo = if (state.scanned.isEmpty()) {
                    0
                } else {
                    state.scanned.size - 1
                }
                scanBinding?.recyclerView?.scrollToPosition(scrollTo)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.per_app_proxy_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_scan_china_apps -> {
            viewModel.scanChinaApps()
            true
        }

        R.id.action_invert_selections -> {
            runOnDefaultDispatcher {
                viewModel.invertSelected()
            }
            true
        }

        R.id.action_clear_selections -> {
            runOnDefaultDispatcher {
                viewModel.clearSelections()
            }
            true
        }

        R.id.action_export_clipboard -> {
            val packagesByLine = viewModel.packages.joinToString("\n")
            val success =
                SagerNet.trySetPrimaryClip("${DataStore.bypassMode}\n${packagesByLine}")
            Snackbar.make(
                binding.list,
                if (success) R.string.action_export_msg else R.string.action_export_err,
                Snackbar.LENGTH_LONG,
            ).show()
            true
        }

        R.id.action_import_clipboard -> {
            val clipboardString =
                SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            runOnDefaultDispatcher {
                viewModel.importFromClipboard(clipboardString)
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private class TextViewAdapter : ListAdapter<String, TextViewHolder>(SimpleDiffCallback()) {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
            val textView = LayoutInflater.from(parent.context).inflate(
                android.R.layout.simple_list_item_1,
                parent,
                false,
            ) as TextView
            return TextViewHolder(textView)
        }

        override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).hashCode().toLong()
        }

    }

    private class TextViewHolder(val view: TextView) : RecyclerView.ViewHolder(view) {
        fun bind(text: String) {
            view.text = text
        }
    }
}
