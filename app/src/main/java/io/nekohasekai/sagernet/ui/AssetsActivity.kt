package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutAssetItemBinding
import io.nekohasekai.sagernet.databinding.LayoutAssetsBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.use
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AssetsActivity : ThemedActivity() {

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
        updating.visibility = View.GONE

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
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.route_assets)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        adapter = AssetAdapter(assetsDir)
        binding.recyclerView.adapter = adapter

        undoManager = UndoSnackbarManager(this, adapter)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.START
        ) {

            override fun getSwipeDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
            ): Int {
                val index = viewHolder.bindingAdapterPosition
                if (index < 2) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.bindingAdapterPosition
                adapter.remove(index)
                undoManager.remove(index to (viewHolder as AssetHolder).file)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = false

        }).attachToRecyclerView(binding.recyclerView)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    private fun handleUiState(state: AssetsUiState) {
        when (state) {
            AssetsUiState.Idle -> {
                updating.setProgressCompat(0, true)
                updating.visibility = View.GONE
                adapter.reloadAssets()
            }

            is AssetsUiState.Doing -> {
                updating.isVisible = true
                updating.setProgressCompat(state.progress, true)
            }

            is AssetsUiState.Done -> {
                updating.setProgressCompat(0, true)
                updating.visibility = View.GONE

                when (state.e) {
                    null -> {
                        snackbar(R.string.route_asset_updated).show()
                        adapter.reloadAssets()
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
                viewModel.importFile(geoDir, tempImportFile)
            }

        }


    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_import_file -> {
            startFilesForResult(importFile, "*/*")
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

    private class AssetAdapter(val assetsDir: File) : RecyclerView.Adapter<AssetHolder>(),
        UndoSnackbarManager.Interface<File> {

        private val assets = ArrayList<File>()

        init {
            reloadAssets()
        }

        fun reloadAssets() {
            assets.clear()
            assets.add(File(assetsDir, "geoip.version.txt"))
            assets.add(File(assetsDir, "geosite.version.txt"))

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetHolder {
            val inflater = LayoutInflater.from(parent.context)
            return AssetHolder(LayoutAssetItemBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: AssetHolder, position: Int) {
            holder.bind(assets[position])
        }

        override fun getItemCount(): Int = assets.size

        fun remove(index: Int) {
            assets.removeAt(index)
            notifyItemRemoved(index)
        }

        override fun undo(actions: List<Pair<Int, File>>) {
            for ((index, item) in actions) {
                assets.add(index, item)
                notifyItemInserted(index)
            }
        }

        override fun commit(actions: List<Pair<Int, File>>) {
            val filesToDelete = actions.mapX { it.second }.toTypedArray()
            runOnDefaultDispatcher {
                filesToDelete.forEach { it.deleteRecursively() }
            }
        }
    }

    private class AssetHolder(val binding: LayoutAssetItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        lateinit var file: File

        fun bind(file: File) {
            this.file = file

            binding.assetName.text = file.name.removeSuffix(".version.txt")

            val localVersion = if (file.isFile) {
                file.readText().trim()
            } else {
                file.writeText("Unknown")
                "Unknown"
            }

            binding.assetStatus.text =
                binding.root.context.getString(R.string.route_asset_status, localVersion)

        }

    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            adapter.reloadAssets()
        }
    }

}
