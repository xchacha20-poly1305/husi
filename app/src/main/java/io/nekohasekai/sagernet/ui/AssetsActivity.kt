package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAssetItemBinding
import io.nekohasekai.sagernet.databinding.LayoutAssetsBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import libcore.Libcore
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

class AssetsActivity : ThemedActivity() {

    lateinit var adapter: AssetAdapter
    lateinit var layout: LayoutAssetsBinding
    lateinit var undoManager: UndoSnackbarManager<File>
    lateinit var updating: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = LayoutAssetsBinding.inflate(layoutInflater)
        layout = binding
        setContentView(binding.root)

        updating = findViewById(R.id.action_updating)
        updating.visibility = View.GONE

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.route_assets)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.recyclerView.layoutManager = FixedLinearLayoutManager(binding.recyclerView)
        adapter = AssetAdapter()
        binding.recyclerView.adapter = adapter

        binding.refreshLayout.setOnRefreshListener {
            adapter.reloadAssets()
            binding.refreshLayout.isRefreshing = false
        }
        binding.refreshLayout.setColorSchemeColors(getColorAttr(R.attr.primaryOrTextPrimary))

        undoManager = UndoSnackbarManager(this, adapter)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.START
        ) {

            override fun getSwipeDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
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
                target: RecyclerView.ViewHolder
            ) = false

        }).attachToRecyclerView(binding.recyclerView)
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(layout.coordinator, text, Snackbar.LENGTH_LONG)
    }

//    val assetNames = listOf("geoip", "geosite")

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

            runOnDefaultDispatcher {
                val filesDir = getExternalFilesDir(null) ?: filesDir
                val geoDir = File(filesDir, "geo")
                if (!geoDir.exists()) {
                    geoDir.mkdirs()
                }

                val outFile = File(filesDir, fileName).apply {
                    parentFile?.mkdirs()
                }
                contentResolver.openInputStream(fileUri)?.use(outFile.outputStream())

                try {
                    Libcore.unzipWithoutDir(outFile.absolutePath, geoDir.absolutePath)
                } catch (_: Exception) {
                    try {
                        Libcore.untargzWihoutDir(outFile.absolutePath, geoDir.absolutePath)
                    } catch (e: Exception) {
                        onMainDispatcher {
                            e.message?.let { snackbar(it).show() }
                        }
                        return@runOnDefaultDispatcher
                    }
                } finally {
                    outFile.delete()
                }

                val nameList = listOf("geosite", "geoip")
                for (name in nameList) {
                    File(filesDir, "$name.version.txt").apply {
                        if (isFile) delete()
                        createNewFile()
                        val fw = FileWriter(this)
                        fw.write("Custom")
                        fw.close()
                    }
                }

                adapter.reloadAssets()
            }

        }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
                return true
            }

            R.id.action_update_all -> {
                runOnDefaultDispatcher {
                    updateAsset()
                }
                return true
            }
        }
        return false
    }

    inner class AssetAdapter : RecyclerView.Adapter<AssetHolder>(),
        UndoSnackbarManager.Interface<File> {

        private val assets = ArrayList<File>()

        init {
            reloadAssets()
        }

        fun reloadAssets() {
            val filesDir = getExternalFilesDir(null) ?: filesDir
            val geoDir = File(filesDir, "geo")
            if (!geoDir.exists()) {
                geoDir.mkdirs()
            }
            assets.clear()
            assets.add(File(filesDir, "geoip.version.txt"))
            assets.add(File(filesDir, "geosite.version.txt"))

            layout.refreshLayout.post {
                notifyDataSetChanged()
            }

            updating.setProgressCompat(0, true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetHolder {
            return AssetHolder(LayoutAssetItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: AssetHolder, position: Int) {
            holder.bind(assets[position])
        }

        override fun getItemCount(): Int {
            return assets.size
        }

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
            val groups = actions.map { it.second }.toTypedArray()
            runOnDefaultDispatcher {
                groups.forEach { it.deleteRecursively() }
            }
        }

    }

//    val updating = AtomicInteger()

    inner class AssetHolder(val binding: LayoutAssetItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        lateinit var file: File

        fun bind(file: File) {
            this.file = file

            binding.assetName.text = file.name.removeSuffix(".version.txt")

            val localVersion = if (file.isFile) {
                file.readText().trim()
            } else {
                "Unknown"
            }

            binding.assetStatus.text = getString(R.string.route_asset_status, localVersion)

        }

    }

    private suspend fun updateAsset() {
        val filesDir = getExternalFilesDir(null) ?: filesDir
        var progress = 0
        fun setProgress(p: Int) {
            progress += p
            updating.setProgressCompat(progress, true)
        }

        val repos: List<String> = when (DataStore.rulesProvider) {
            0 -> listOf("SagerNet/sing-geoip", "SagerNet/sing-geosite")
            1 -> listOf("xchacha20-poly1305/sing-geoip", "xchacha20-poly1305/sing-geosite")
            2 -> listOf("Chocolate4U/Iran-sing-box-rules", "Chocolate4U/Iran-sing-box-rules")
            else -> listOf("SagerNet/sing-geoip", "SagerNet/sing-geosite")
        }

        onMainDispatcher {
            updating.visibility = View.VISIBLE
            setProgress(15)
        }

        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort)
        }


        val versionFileList: List<File> = listOf(
            File(filesDir, "geoip.version.txt"),
            File(filesDir, "geosite.version.txt")
        )

        fun getVersion(repo: String): String {
            try {
                val response = client.newRequest().apply {
                    setURL("https://api.github.com/repos/$repo/releases/latest")
                }.execute()
                return JSONObject(response.contentString).optString("tag_name")
            } catch (e: Exception) {
                return ""
            }
        }

        val remoteVersionList = listOf(
            getVersion(repos[0]),
            getVersion(repos[1])
        )
        if (remoteVersionList[0] == versionFileList[0].readText() &&
            remoteVersionList[1] == versionFileList[1].readText()
        ) {
            onMainDispatcher {
                updating.visibility = View.GONE
                snackbar(R.string.route_asset_no_update).show()
            }
            return
        }


        val geoDir = File(filesDir, "geo")
        var cacheFiles: Array<File> = arrayOf()

        repos.forEachIndexed { i, repo ->
            try {
                // https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set
                val response = client.newRequest().apply {
                    setURL("https://codeload.github.com/$repo/tar.gz/refs/heads/rule-set")
                }.execute()

                val cacheFile = File(filesDir.parentFile, filesDir.name + i + ".tmp")
                cacheFile.parentFile?.mkdirs()
                response.writeTo(cacheFile.canonicalPath)
                cacheFiles += cacheFile

            } catch (e: Exception) {
                Logs.e(e)
                onMainDispatcher {
                    e.message?.let { snackbar(it).show() }
                }
            } finally {
                client.close()
            }

            onMainDispatcher {
                setProgress(20)
            }

        }

        for (cacheFile in cacheFiles) {
            onMainDispatcher {
                setProgress(15)
            }
            Libcore.untargzWihoutDir(cacheFile.absolutePath, geoDir.absolutePath)
            cacheFile.delete()
        }

        versionFileList.forEachIndexed { i, versionFile ->
            onMainDispatcher {
                setProgress(5)
            }
            versionFile.writeText(remoteVersionList[i])
        }

        progress = 100
        onMainDispatcher {
            setProgress(0)
            updating.visibility = View.GONE
            adapter.reloadAssets()
            snackbar(R.string.route_asset_updated).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            adapter.reloadAssets()
        }
    }


}
