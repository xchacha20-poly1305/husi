package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppsBinding
import io.nekohasekai.sagernet.databinding.LayoutLoadingBinding
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.utils.SimpleDiffCallback
import kotlinx.coroutines.launch

class AppManagerActivity : ThemedActivity() {

    private val viewModel by viewModels<AppManagerActivityViewModel>()
    private lateinit var binding: LayoutAppsBinding
    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setContent {
            AppTheme {
                val toolbarState by viewModel.toolbarState.collectAsStateWithLifecycle()
                var searchActivate by remember { mutableStateOf(false) }
                val focusManager = LocalFocusManager.current
                var showOverflowMenu by remember { mutableStateOf(false) }
                TopAppBar(
                    title = {
                        if (searchActivate) {
                            OutlinedTextField(
                                value = toolbarState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(android.R.string.search_go)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                }),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f
                                    ),
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.2f
                                    ),
                                ),
                            )
                        } else {
                            Text(stringResource(R.string.proxied_apps))
                        }
                    },
                    navigationIcon = {
                        SimpleIconButton(Icons.Filled.Close) {
                            onBackPressedDispatcher.onBackPressed()
                        }
                    },
                    actions = {
                        if (searchActivate) SimpleIconButton(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            onClick = {
                                viewModel.setSearchQuery("")
                                searchActivate = false
                            },
                        ) else {
                            SimpleIconButton(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(android.R.string.search_go),
                                onClick = { searchActivate = true },
                            )

                            SimpleIconButton(
                                imageVector = Icons.Filled.CopyAll,
                                contentDescription = stringResource(R.string.action_copy),
                            ) {
                                val toExport = viewModel.export()
                                val success = SagerNet.trySetPrimaryClip(toExport)
                                snackbar(
                                    if (success) {
                                        R.string.copy_success
                                    } else {
                                        R.string.copy_failed
                                    }
                                ).show()
                            }
                            SimpleIconButton(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.action_import)
                            ) {
                                viewModel.import(SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                            }

                            Box {
                                SimpleIconButton(Icons.Filled.MoreVert) {
                                    showOverflowMenu = true
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_scan_china_apps)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.scanChinaApps()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.invert_selections)) },
                                        onClick = {
                                            viewModel.invertSections()
                                            showOverflowMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.clear_selections)) },
                                        onClick = {
                                            viewModel.clearSections()
                                            showOverflowMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.list) { v, insets ->
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

        binding.list.itemAnimator = DefaultItemAnimator()
        binding.list.adapter = AppsAdapter(packageManager) { app ->
            viewModel.onItemClick(app)
        }.also {
            adapter = it
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }

        viewModel.initialize(packageManager)
    }

    private var scanDialog: AlertDialog? = null
    private var scanBinding: LayoutLoadingBinding? = null
    private var scanText: TextViewAdapter? = null

    private fun handleUiState(state: AppManagerUiState) {
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
                    .setCancelable(false)
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

        if (state.isLoading) {
            binding.loading.crossFadeFrom(binding.list)
        } else {
            binding.list.crossFadeFrom(binding.loading)
        }
        adapter.submitList(state.apps)
    }

    private fun handleUiEvent(event: AppManagerUiEvent) {
        when (event) {
            is AppManagerUiEvent.Snackbar -> {
                snackbar(getStringOrRes(event.message)).show()
            }
        }
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.list, text, Snackbar.LENGTH_LONG)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
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
