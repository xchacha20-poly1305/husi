package io.nekohasekai.sagernet.ui.profile

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.blacksquircle.ui.editorkit.insert
import com.blacksquircle.ui.editorkit.model.ColorScheme
import com.blacksquircle.ui.editorkit.plugin.autoindent.autoIndentation
import com.blacksquircle.ui.editorkit.plugin.base.PluginSupplier
import com.blacksquircle.ui.editorkit.plugin.delimiters.highlightDelimiters
import com.blacksquircle.ui.editorkit.plugin.linenumbers.lineNumbers
import com.blacksquircle.ui.language.json.JsonLanguage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutEditConfigBinding
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.launch
import kotlin.math.max

class ConfigEditActivity : ThemedActivity() {

    companion object {
        const val EXTRA_CUSTOM_CONFIG = "custom_config"
    }

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            MaterialAlertDialogBuilder(this@ConfigEditActivity)
                .setTitle(R.string.unsaved_changes_prompt)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runOnDefaultDispatcher {
                        saveAndExit()
                    }
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    finish()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }
    }

    private lateinit var binding: LayoutEditConfigBinding
    private val viewModel by viewModels<ConfigEditActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.extras?.getString(EXTRA_CUSTOM_CONFIG)?.let {
            viewModel.key = it
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
            useSpacesInsteadOfTabs = true
            // https://github.com/SagerNet/sing-box/blob/43fef1dae66e581309d62bb4421df4d07683a49c/experimental/libbox/config.go#L166C25-L166C27
            tabWidth = 2
            language = JsonLanguage()
            setHorizontallyScrolling(true)
            viewModel.content = DataStore.profileCacheStore.getString(viewModel.key)!!
            setTextContent(viewModel.content)
            colorScheme = editorScheme
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
            addTextChangedListener {
                viewModel.content = it.toString()
                if (!onBackPressedCallback.isEnabled) {
                    viewModel.needSave = true
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
            lifecycleScope.launch {
                viewModel.formatJson(binding.editor.text)
            }
        }
        binding.actionConfigTest.setOnClickListener {
            lifecycleScope.launch {
                viewModel.checkConfig(binding.editor.text.toString())
            }
        }

        binding.extendedKeyboard.apply {
            setKeyListener { char ->
                binding.editor.insert(char)
            }
            setHasFixedSize(true)
            submitList(listOf("{", "}", ",", ":", "_", "\""))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }

        onBackPressedCallback.isEnabled = viewModel.needSave
    }

    private fun handleUiEvent(event: ConfigEditActivityUiEvent) {
        when (event) {
            is ConfigEditActivityUiEvent.UpdateText -> {
                binding.editor.setText(event.text)
            }

            is ConfigEditActivityUiEvent.Alert -> alert(event.message).show()
            is ConfigEditActivityUiEvent.SnackBar -> snackbar(event.id).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            if (currentFocus == binding.editor) {
                binding.editor.insert(binding.editor.tab())
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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
                runOnDefaultDispatcher {
                    saveAndExit()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT)
    }

    private suspend fun saveAndExit() {
        onIoDispatcher {
            viewModel.saveToDataStore(binding.editor.text.toString())
        }
        setResult(RESULT_OK)
        finish()
    }

    private val editorScheme: ColorScheme
        get() {
            val colorPrimary = getColorAttr(com.google.android.material.R.attr.colorPrimary)
            val colorPrimaryDark = getColorAttr(com.google.android.material.R.attr.colorPrimaryDark)
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