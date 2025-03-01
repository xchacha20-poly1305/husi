package io.nekohasekai.sagernet.ui.profile

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import com.blacksquircle.ui.editorkit.insert
import com.blacksquircle.ui.language.json.JsonLanguage
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutEditConfigBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ui.ThemedActivity
import libcore.Libcore
import moe.matsuri.nb4a.ui.ExtendedKeyboard

class ConfigEditActivity : ThemedActivity() {

    private var dirty = false
    var key = Key.SERVER_CONFIG

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            UnsavedChangesDialogFragment().apply {
                key()
            }.show(supportFragmentManager, null)
        }
    }

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(android.R.string.ok) { _, _ ->
                (requireActivity() as ConfigEditActivity).saveAndExit()
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    lateinit var binding: LayoutEditConfigBinding

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.extras?.apply {
            getString("key")?.let { key = it }
        }

        binding = LayoutEditConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.config_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.editor.apply {
            language = JsonLanguage()
            setHorizontallyScrolling(true)
            setTextContent(DataStore.profileCacheStore.getString(key)!!)
            addTextChangedListener {
                if (!dirty) {
                    dirty = true
                    DataStore.dirty = true
                    onBackPressedCallback.isEnabled = true
                }
            }
        }

        binding.actionTab.setOnClickListener {
            binding.editor.insert(binding.editor.tab())
        }
        binding.actionUndo.setOnClickListener {
            try {
                binding.editor.undo()
            } catch (_: Exception) {
            }
        }
        binding.actionRedo.setOnClickListener {
            try {
                binding.editor.redo()
            } catch (_: Exception) {
            }
        }
        binding.actionFormat.setOnClickListener {
            formatText()?.let {
                binding.editor.setTextContent(it)
            }
        }
        binding.actionConfigTest.setOnClickListener {
            try {
                val content = binding.editor.text.toString()
                val jsonContent = if (content.contains("outbound")) {
                    content
                } else {
                    // make it full outbounds
                    val singleOutbound = JsonParser.parseString(content)
                    val jsonArray = JsonArray().also { it.add(singleOutbound) }
                    JsonObject().also { it.add("outbounds", jsonArray) }.toString()
                }
                Libcore.checkConfig(jsonContent)
            } catch (e: Exception) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(e.toString())
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
                return@setOnClickListener
            }
            snackbar(android.R.string.ok).show()
        }

        val extendedKeyboard = findViewById<ExtendedKeyboard>(R.id.extended_keyboard)
        extendedKeyboard.setKeyListener { char -> binding.editor.insert(char) }
        extendedKeyboard.setHasFixedSize(true)
        extendedKeyboard.submitList("{},:_\"".map { it.toString() })
        extendedKeyboard.setBackgroundColor(getColorAttr(R.attr.primaryOrTextPrimary))
    }

    private fun formatText(): String? {
        try {
            val txt = binding.editor.text.toString()
            if (txt.isBlank()) {
                return ""
            }
            return Libcore.formatConfig(txt).value
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_title)
                .setMessage(e.readableMessage)
                .show()
            return null
        }
    }

    fun saveAndExit() {
        formatText()?.let {
            DataStore.profileCacheStore.putString(key, it)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_apply_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_apply -> {
                saveAndExit()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT)
    }
}