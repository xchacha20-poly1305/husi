package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutAssetItemBinding
import io.nekohasekai.sagernet.databinding.LayoutAssetsBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.alertAndLog
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.use
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AssetsActivity : ThemedActivity(), UndoSnackbarManager.Interface<File> {

    private val viewModel: AssetsActivityViewModel by viewModels()
    private lateinit var adapter: AssetAdapter
    private lateinit var layout: LayoutAssetsBinding
    private lateinit var undoManager: UndoSnackbarManager<File>
    private lateinit var updating: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = LayoutAssetsBinding.inflate(layoutInflater)
        layout = binding
        setContentView(binding.root)

        updating = findViewById(R.id.action_updating)
        updating.isGone = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.route_assets)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        adapter = AssetAdapter()
        binding.recyclerView.adapter = adapter

        undoManager = UndoSnackbarManager(this, this)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.START
        ) {

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val idx = viewHolder.bindingAdapterPosition
                val item = adapter.currentList.getOrNull(idx)
                if (item?.builtIn == true) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.bindingAdapterPosition
                val file = (viewHolder as AssetHolder).item.file
                adapter.remove(index)
                undoManager.remove(index to file)
            }


            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = false

        }).attachToRecyclerView(binding.recyclerView)

        lifecycleScope.launch {
            viewModel.initialize(assetsDir, geoDir)
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.assets.collect { assets ->
                    adapter.submitList(assets)
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }
    }

    private fun handleUiState(state: AssetsUiState) {
        when (state) {
            AssetsUiState.Idle -> {
                updating.setProgressCompat(0, true)
                updating.isGone = true
            }

            is AssetsUiState.Doing -> {
                updating.isVisible = true
                updating.setProgressCompat(state.progress, true)
            }

            is AssetsUiState.Done -> {
                updating.setProgressCompat(0, true)
                updating.isGone = true

                when (state.e) {
                    null -> {
                        snackbar(R.string.route_asset_updated).show()
                    }

                    is NoUpdateException -> {
                        snackbar(R.string.route_asset_no_update).show()
                    }

                    else -> {
                        Logs.e(state.e)
                        snackbar(state.e.readableMessage).show()
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: AssetEvent) {
        when (event) {
            is AssetEvent.UpdateItem -> {
                val index =
                    adapter.currentList.indexOfFirst { it.file.absolutePath == event.asset.absolutePath }
                if (index == -1) return

                val holder =
                    layout.recyclerView.findViewHolderForAdapterPosition(index) as? AssetHolder

                holder?.updateUiState(event.state)
            }
        }
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(layout.coordinator, text, Snackbar.LENGTH_LONG)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.asset_menu, menu)
        return true
    }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { fileUri ->
            if (fileUri == null) return@registerForActivityResult
            val fileName = contentResolver.query(fileUri, null, null, null, null)
                ?.use { cursor ->
                    cursor.moveToFirst()
                    cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                        .let(cursor::getString)
                }?.takeIf { it.isNotBlank() } ?: fileUri.path
            if (fileName == null) return@registerForActivityResult

            lifecycleScope.launch(Dispatchers.IO) {
                val tempImportFile = File(cacheDir, fileName).apply {
                    parentFile?.mkdirs()
                }
                contentResolver.openInputStream(fileUri)?.use(tempImportFile.outputStream())
                viewModel.importFile(tempImportFile, geoDir)
            }

        }

    private val importUrl = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val assetName = result.data?.getStringExtra(AssetEditActivity.EXTRA_ASSET_NAME)

        when (result.resultCode) {
            RESULT_OK -> runOnDefaultDispatcher {
                viewModel.refreshAssets()
            }

            AssetEditActivity.RESULT_SHOULD_UPDATE -> runOnDefaultDispatcher {
                viewModel.refreshAssets()
                viewModel.updateSingleAsset(File(geoDir, assetName!!))
            }

            AssetEditActivity.RESULT_DELETE -> runOnDefaultDispatcher {
                viewModel.deleteAssets(listOf(File(geoDir, assetName!!)))
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_import_file -> {
            startFilesForResult(importFile, "*/*")
            true
        }

        R.id.action_import_url -> {
            importUrl.launch(Intent(this, AssetEditActivity::class.java))
            true
        }

        R.id.action_update_all -> {
            viewModel.updateAsset(destinationDir = geoDir, cacheDir = cacheDir)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private val assetsDir: File by lazy {
        val dir = getExternalFilesDir(null) ?: filesDir
        dir.mkdirs()
        dir
    }

    private val geoDir: File by lazy {
        File(assetsDir, "geo").also { it.mkdirs() }
    }


    private inner class AssetAdapter : ListAdapter<AssetListItem, AssetHolder>(AssetDiffCallback) {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetHolder {
            val inflater = LayoutInflater.from(parent.context)
            return AssetHolder(LayoutAssetItemBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: AssetHolder, position: Int) {
            holder.bind(getItem(position))
        }

        fun remove(index: Int) {
            val current = currentList.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                submitList(current)
            }
        }

        fun addAssetsIndex(index: Int, file: File) {
            val versionFile = File(assetsDir, "${file.name}.version.txt")
            val version = if (versionFile.isFile) versionFile.readText().trim()
                .ifBlank { "Unknown" } else "Unknown"
            val item =
                AssetListItem(file, version, builtIn = AssetsActivityViewModel.isBuiltIn(index))
            val current = currentList.toMutableList()
            val safeIndex = index.coerceIn(0, current.size)
            current.add(safeIndex, item)
            submitList(current)
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).file.absolutePath.hashCode().toLong()
        }
    }

    private object AssetDiffCallback : DiffUtil.ItemCallback<AssetListItem>() {
        override fun areItemsTheSame(oldItem: AssetListItem, newItem: AssetListItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: AssetListItem, newItem: AssetListItem): Boolean {
            return oldItem.version == newItem.version &&
                    oldItem.file.name == newItem.file.name &&
                    oldItem.builtIn == newItem.builtIn
        }
    }

    private inner class AssetHolder(val binding: LayoutAssetItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        lateinit var item: AssetListItem

        fun bind(item: AssetListItem) {
            this.item = item

            val file = item.file
            val isVersionName = file.name.endsWith(".version.txt")

            binding.assetName.text = if (isVersionName) {
                file.name.removeSuffix(".version.txt")
            } else {
                file.name
            }

            binding.assetStatus.text =
                binding.assetStatus.context.getString(R.string.route_asset_status, item.version)

            if (item.builtIn) {
                binding.edit.isVisible = false
                binding.edit.setOnClickListener(null)

                binding.rulesUpdate.isVisible = false
                binding.rulesUpdate.setOnClickListener(null)
            } else {
                binding.edit.isVisible = true
                binding.edit.setOnClickListener {
                    importUrl.launch(
                        Intent(this@AssetsActivity, AssetEditActivity::class.java)
                            .putExtra(AssetEditActivity.EXTRA_ASSET_NAME, file.name)
                    )
                }

                binding.rulesUpdate.isVisible = true
                binding.rulesUpdate.setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) {
                        viewModel.updateSingleAsset(file)
                    }
                }
            }
        }

        fun updateUiState(state: AssetItemUiState) {
            when (state) {
                is AssetItemUiState.Doing -> {
                    binding.rulesUpdate.isVisible = false
                    binding.subscriptionUpdateProgress.isVisible = true
                    binding.subscriptionUpdateProgress.setProgressCompat(state.progress, true)
                }

                is AssetItemUiState.Done -> {
                    binding.subscriptionUpdateProgress.setProgressCompat(0, true)
                    binding.subscriptionUpdateProgress.isInvisible = true
                    binding.rulesUpdate.isVisible = !item.builtIn

                    if (state.e == null) {
                        snackbar(R.string.route_asset_updated).show()
                    } else {
                        alertAndLog(state.e)
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun undo(actions: List<Pair<Int, File>>) {
        for ((index, item) in actions) {
            adapter.addAssetsIndex(index, item)
            adapter.notifyItemInserted(index)
        }
    }

    override fun commit(actions: List<Pair<Int, File>>) {
        // Store file first to prevent list be cleared. FIXME this is the duty of undo manager.
        val filesToDelete = actions.map { it.second }
        runOnDefaultDispatcher {
            viewModel.deleteAssets(filesToDelete)
        }
    }

}
