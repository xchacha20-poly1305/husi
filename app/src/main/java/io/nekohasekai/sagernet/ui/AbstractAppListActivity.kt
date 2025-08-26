package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.launch

abstract class AbstractAppListActivity : ThemedActivity() {
    internal abstract val viewModel: AbstractAppListViewModel

    protected abstract fun bind(layoutInflater: LayoutInflater)
    protected abstract val root: View
    @get:StringRes protected abstract val toolbarTitle: Int
    protected abstract val toolbar: MaterialToolbar
    protected abstract val collapsingToolbar: CollapsingToolbarLayout
    protected abstract val search: TextInputEditText
    protected abstract val showSystemApps: Chip
    protected abstract val loading: LinearLayout
    protected abstract val list: RecyclerView
    protected abstract val snackbarAnchor: View
    private lateinit var appsAdapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind(layoutInflater)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(collapsingToolbar) { v, insets ->
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
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
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
            setTitle(toolbarTitle)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        list.itemAnimator = DefaultItemAnimator()
        list.adapter = AppsAdapter(packageManager) { app ->
            runOnIoDispatcher {
                viewModel.onItemClick(app)
            }
        }.also {
            appsAdapter = it
        }

        search.setText(viewModel.searchText)
        search.addTextChangedListener {
            viewModel.searchText = it.toString()
        }

        showSystemApps.isChecked = viewModel.showSystemApp
        showSystemApps.setOnCheckedChangeListener { _, isChecked ->
            viewModel.showSystemApp = isChecked
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }

        loading.crossFadeFrom(list)
        viewModel.initialize(packageManager)
        list.crossFadeFrom(loading)
    }

     internal open fun handleUiEvent(event: AbstractAppListUiEvent) {
        when (event) {
            is AbstractAppListUiEvent.Snackbar -> {
                snackbar(getStringOrRes(event.message)).show()
            }
        }
    }

    internal open fun handleUiState(state: AbstractAppListUiState) {
        appsAdapter.submitList(state.base.apps)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?) = if (keyCode == KeyEvent.KEYCODE_MENU) {
        if (toolbar.isOverflowMenuShowing) {
            toolbar.hideOverflowMenu()
        } else {
            toolbar.showOverflowMenu()
        }
    } else {
        super.onKeyUp(keyCode, event)
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(snackbarAnchor, text, Snackbar.LENGTH_LONG)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun supportNavigateUpTo(upIntent: Intent) =
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    internal class AppsAdapter(
        val packageManager: PackageManager,
        val onItemClick: (ProxiedApp) -> Unit,
    ) : ListAdapter<ProxiedApp, AppViewHolder>(ProxiedAppDiffCallback) {

        init {
            setHasStableIds(true)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item, packageManager, onItemClick)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any?>) {
            if (payloads.contains(ProxiedAppDiffCallback.PAYLOAD_CHECKED)) {
                holder.binding.itemcheck.isChecked = getItem(position).isProxied
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            return AppViewHolder(
                LayoutAppsItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).packageName.hashCode().toLong()
        }

    }

    internal class AppViewHolder(val binding: LayoutAppsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        var uid = 0

        fun bind(app: ProxiedApp, packageManager: PackageManager, onClick: (ProxiedApp) -> Unit) {
            uid = app.uid

            binding.itemicon.setImageDrawable(app.loadIcon(packageManager))
            binding.title.text = app.name
            binding.desc.text = "${app.packageName} (${uid})"
            binding.itemcheck.isChecked = app.isProxied
            binding.root.setOnClickListener {
                onClick(app)
            }
        }
    }
}