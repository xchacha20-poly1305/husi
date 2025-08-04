package io.nekohasekai.sagernet.ui.profile

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.blacksquircle.ui.editorkit.insert
import com.blacksquircle.ui.editorkit.model.ColorScheme
import com.blacksquircle.ui.editorkit.plugin.autoindent.autoIndentation
import com.blacksquircle.ui.editorkit.plugin.base.PluginSupplier
import com.blacksquircle.ui.editorkit.plugin.delimiters.highlightDelimiters
import com.blacksquircle.ui.editorkit.plugin.dirtytext.OnChangeListener
import com.blacksquircle.ui.editorkit.plugin.dirtytext.onChangeListener
import com.blacksquircle.ui.editorkit.plugin.linenumbers.lineNumbers
import com.blacksquircle.ui.language.json.JsonLanguage
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutEditConfigBinding
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.utils.Theme
import libcore.Libcore
import kotlin.math.max

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

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>(Empty::class.java) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.extras?.apply {
            getString("key")?.let { key = it }
        }

        binding = LayoutEditConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.editor) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.keyboardContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = max(bars.bottom, ime.bottom),
            )
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.config_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.editor.apply {
            language = JsonLanguage()
            setHorizontallyScrolling(true)
            setTextContent(DataStore.profileCacheStore.getString(key)!!)
            colorScheme = myTheme
            plugins(PluginSupplier.create {
                lineNumbers {
                    lineNumbers = true
                    highlightCurrentLine = true
                }
                highlightDelimiters()
                autoIndentation {
                    autoIndentLines = true
                    autoCloseBrackets = true
                    autoCloseQuotes = true
                }
            })
            onChangeListener = OnChangeListener {
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

        binding.extendedKeyboard.apply {
            setKeyListener { char -> binding.editor.insert(char) }
            setHasFixedSize(true)
            submitList(listOf("{", "}", ",", ":", "_", "\""))
        }
    }

    private fun formatText(): String? {
        try {
            val txt = binding.editor.text.toString()
            if (txt.isBlank()) {
                return ""
            }
            return Libcore.formatConfig(txt).value
        } catch (e: Exception) {
            alert(e.readableMessage).show()
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

    val myTheme: ColorScheme
        get() {
            val colorPrimary = getColorAttr(androidx.appcompat.R.attr.colorPrimary)
            val colorPrimaryDark = getColorAttr(androidx.appcompat.R.attr.colorPrimaryDark)
            val appTheme = Theme.getTheme()
            val nightMode = Theme.usingNightMode()

            return ColorScheme(
                textColor = when (appTheme) {
                    R.style.Theme_SagerNet_Black -> if (nightMode) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }

                    else -> colorPrimary
                },
                cursorColor = "#BBBBBB".toColorInt(),
                backgroundColor = if (nightMode) {
                    Color.BLACK
                } else {
                    Color.WHITE
                },
                gutterColor = colorPrimary,
                gutterDividerColor = if (nightMode) {
                    Color.BLACK
                } else {
                    Color.WHITE
                },
                gutterCurrentLineNumberColor = when (appTheme) {
                    R.style.Theme_SagerNet_Black -> if (nightMode) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }

                    else -> Color.WHITE
                },
                gutterTextColor = when (appTheme) {
                    R.style.Theme_SagerNet_Black -> if (nightMode) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }

                    else -> Color.WHITE
                },
                selectedLineColor = if (nightMode) {
                    "#2C2C2C".toColorInt()
                } else {
                    "#D3D3D3".toColorInt()
                },
                selectionColor = when (appTheme) {
                    R.style.Theme_SagerNet_Black -> if (nightMode) {
                        "#4C4C4C".toColorInt()
                    } else {
                        "#B3B3B3".toColorInt()
                    }

                    else -> colorPrimary
                },
                suggestionQueryColor = "#7CE0F3".toColorInt(),
                findResultBackgroundColor = "#5F5E5A".toColorInt(),
                delimiterBackgroundColor = "#5F5E5A".toColorInt(),
                numberColor = "#BB8FF8".toColorInt(),
                operatorColor = if (nightMode) {
                    Color.WHITE
                } else {
                    Color.BLACK
                },
                keywordColor = "#EB347E".toColorInt(),
                typeColor = "#7FD0E4".toColorInt(),
                langConstColor = "#EB347E".toColorInt(),
                preprocessorColor = "#EB347E".toColorInt(),
                variableColor = "#7FD0E4".toColorInt(),
                methodColor = "#B6E951".toColorInt(),
                stringColor = when (Theme.getTheme()) {
                    R.style.Theme_SagerNet_Black -> if (nightMode) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }

                    else -> colorPrimaryDark
                },
                commentColor = "#89826D".toColorInt(),
                tagColor = "#F8F8F8".toColorInt(),
                tagNameColor = "#EB347E".toColorInt(),
                attrNameColor = "#B6E951".toColorInt(),
                attrValueColor = "#EBE48C".toColorInt(),
                entityRefColor = "#BB8FF8".toColorInt(),
            )
        }
}