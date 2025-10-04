package io.nekohasekai.sagernet.ui

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
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
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.databinding.LayoutAppListBinding
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.first
import io.nekohasekai.sagernet.ktx.trySetPrimaryClip
import kotlinx.coroutines.launch

class AppListActivity : ThemedActivity() {

    companion object {
        const val EXTRA_APP_LIST = "app_list"
    }

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = true) {
        override fun handleOnBackPressed() {
            saveAndExit()
        }
    }

    private val viewModel by viewModels<AppListActivityViewModel>()
    private lateinit var binding: LayoutAppListBinding
    private lateinit var adapter: AppsAdapter

    private val clipboard by lazy { getSystemService<ClipboardManager>()!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutAppListBinding.inflate(layoutInflater)
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
                            Text(stringResource(R.string.select_apps))
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
                        ) {
                            searchActivate = false
                            viewModel.setSearchQuery("")
                        } else {
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
                                val success = clipboard.trySetPrimaryClip(toExport)
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
                                contentDescription = stringResource(R.string.action_import),
                            ) {
                                viewModel.import(clipboard.first())
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

        val packages = intent.getStringArrayExtra(EXTRA_APP_LIST)?.toSet() ?: emptySet()
        viewModel.initialize(packageManager, packages)
    }

    private fun handleUiState(state: AppListActivityUiState) {
        if (state.isLoading) {
            binding.loading.crossFadeFrom(binding.list)
        } else {
            binding.list.crossFadeFrom(binding.loading)
        }
        adapter.submitList(state.apps)
    }

    private fun handleUiEvent(event: AppListActivityUIEvent) {
        when (event) {
            is AppListActivityUIEvent.Snackbar -> {
                snackbar(getStringOrRes(event.message)).show()
            }
        }
    }

    private fun saveAndExit() {
        setResult(
            RESULT_OK,
            Intent()
                .putStringArrayListExtra(EXTRA_APP_LIST, viewModel.allPackages()),
        )
        finish()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.list, text, Snackbar.LENGTH_LONG)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
