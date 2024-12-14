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
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAssetItemBinding
import io.nekohasekai.sagernet.databinding.LayoutAssetsBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.use
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import libcore.HTTPClient
import libcore.Libcore
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import kotlin.io.path.absolutePathString

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
                    tryOpenCompressed(outFile.absolutePath, geoDir.absolutePath)
                } catch (e: Exception) {
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                    return@runOnDefaultDispatcher
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

    inner class AssetHolder(val binding: LayoutAssetItemBinding) :
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

            binding.assetStatus.text = getString(R.string.route_asset_status, localVersion)

        }

    }

    interface RulesFetcher {
        val versionFiles: List<File>
        val updateAssets: ((Int) -> Unit)

        /**
         * Fetch rules
         * @return true means not need to update.
         */
        fun fetch(): Boolean
    }

    private class GithubRuleFetcher(
        override val versionFiles: List<File>,
        override val updateAssets: ((Int) -> Unit),
        val repos: List<String>,
        val client: HTTPClient,
        val geoDir: String,
        val unstableBranch: Boolean = false, // rule-set v2
    ) : RulesFetcher {
        override fun fetch(): Boolean {
            // Compare version
            val versions = mutableListOf<String>()
            var shouldUpdate = false
            for ((i, repo) in repos.withIndex()) {
                val version = fetchVersion(repo)
                updateAssets(5)
                versions.add(version)
                if (versionFiles[i].readText() != version) {
                    shouldUpdate = true
                }
            }
            if (!shouldUpdate) return true

            // single repo
            while (versions.size == repos.size) {
                versions.add(versions[0])
            }

            // Download
            val cacheFiles = mutableListOf<File>()
            for (repo in repos) {
                cacheFiles.add(download(repo))
                updateAssets(5)
            }

            try {
                for (file in cacheFiles) {
                    Libcore.untargzWithoutDir(file.absolutePath, geoDir)
                    updateAssets(10)
                }
            } catch (e: Exception) {
                throw e
            } finally {
                // Make sure delete cache
                for (file in cacheFiles) {
                    file.delete()
                }
            }

            // Write version
            for ((i, version) in versionFiles.withIndex()) {
                version.writeText(versions[i])
                updateAssets(5)
            }

            return false
        }

        private fun fetchVersion(repo: String): String {
            val response = client.newRequest().apply {
                setURL("https://api.github.com/repos/$repo/releases/latest")
                setUserAgent(USER_AGENT)
            }.execute()
            return JSONObject(response.contentString.value).optString("tag_name")
        }

        private fun download(repo: String): File {
            // https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set
            var branchName = "rule-set"
            if (unstableBranch && repo.endsWith("sing-geosite")) branchName += "-unstable"
            val response = client.newRequest().apply {
                setURL("https://codeload.github.com/$repo/tar.gz/refs/heads/${branchName}")
                setUserAgent(USER_AGENT)
            }.execute()

            val cacheFile = File(
                kotlin.io.path.createTempFile(repo.substringAfter("/"), "tmp").absolutePathString()
            )
            cacheFile.parentFile?.mkdirs()
            response.writeTo(cacheFile.absolutePath, null)
            return cacheFile
        }
    }

    private suspend fun updateAsset() {
        val filesDir = getExternalFilesDir(null) ?: filesDir
        val geoDir = File(filesDir, "geo")
        var progress = 0
        val updateProgress: (p: Int) -> Unit = { p ->
            progress += p
            updating.setProgressCompat(progress, true)
        }

        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
        }

        val versionFileList: List<File> = listOf(
            File(filesDir, "geoip.version.txt"),
            File(filesDir, "geosite.version.txt")
        )
        val provider: RulesFetcher = if (DataStore.rulesProvider != RuleProvider.CUSTOM) {
            GithubRuleFetcher(
                versionFileList,
                updateProgress,
                when (DataStore.rulesProvider) {
                    RuleProvider.OFFICIAL -> {
                        listOf("SagerNet/sing-geoip", "SagerNet/sing-geosite")
                    }

                    RuleProvider.LOYALSOLDIER -> {
                        listOf("xchacha20-poly1305/sing-geoip", "xchacha20-poly1305/sing-geosite")
                    }

                    RuleProvider.CHOCOLATE4U -> listOf("Chocolate4U/Iran-sing-box-rules")

                    else -> error("?")
                },
                client,
                geoDir.absolutePath,
                RuleProvider.hasUnstableBranch(DataStore.rulesProvider),
            )
        } else {
            object : RulesFetcher {
                override val versionFiles: List<File> = versionFileList
                override val updateAssets: (Int) -> Unit = updateProgress

                override fun fetch(): Boolean {
                    val links = DataStore.customRuleProvider.listByLineOrComma()
                    val cacheFiles = mutableListOf<File>()

                    updateProgress(35)
                    for ((i, link) in links.withIndex()) {
                        val response = client.newRequest().apply {
                            setURL(link)
                            setUserAgent(USER_AGENT)
                        }.execute()

                        val cacheFile = File(cacheDir.parentFile, cacheDir.name + i + ".tmp")
                        cacheFile.parentFile?.mkdirs()
                        response.writeTo(cacheFile.canonicalPath, null)
                        cacheFiles.add(cacheFile)
                    }

                    updateProgress(25)
                    try {
                        for (file in cacheFiles) {
                            tryOpenCompressed(file.absolutePath, geoDir.absolutePath)
                        }
                    } catch (e: Exception) {
                        throw e
                    } finally {
                        for (file in cacheFiles) {
                            file.delete()
                        }
                    }

                    updateProgress(25)
                    for (version in versionFiles) {
                        version.writeText("custom")
                    }

                    return false
                }
            }
        }

        onMainDispatcher {
            updating.visibility = View.VISIBLE
            updateProgress(15)
        }

        var notNeedUpdate = false
        runCatching {
            notNeedUpdate = provider.fetch()
        }.onFailure { e ->
            Logs.e(e)
            snackbar(e.readableMessage).show()
        }.onSuccess {
            if (notNeedUpdate) {
                snackbar(R.string.route_asset_no_update).show()
            } else {
                snackbar(R.string.route_asset_updated).show()
            }
        }

        updating.setProgressCompat(100, true)
        onMainDispatcher {
            updating.visibility = View.GONE
            adapter.reloadAssets()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    @Deprecated(
        "This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.",
        ReplaceWith("finish()")
    )
    override fun onBackPressed() {
        finish()
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            adapter.reloadAssets()
        }
    }

    private fun tryOpenCompressed(from: String, toDir: String) {
        runCatching {
            Libcore.untargzWithoutDir(from, toDir)
        }.onSuccess { return }
        runCatching {
            Libcore.unzipWithoutDir(from, toDir)
        }.onSuccess { return }
        error("unknown file")
    }

}
