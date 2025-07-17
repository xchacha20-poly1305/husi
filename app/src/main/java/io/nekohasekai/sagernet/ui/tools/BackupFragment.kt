package io.nekohasekai.sagernet.ui.tools

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutBackupBinding
import io.nekohasekai.sagernet.databinding.LayoutImportBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class BackupFragment : NamedFragment(R.layout.layout_backup) {
    override fun getName(context: Context) = context.getString(R.string.backup)

    private val viewModel: BackupViewModel by viewModels()
    private lateinit var binding: LayoutBackupBinding

    private var contentToExport: String = ""
    private var progressDialog: AlertDialog? = null

    private val exportSettings =
        registerForActivityResult(CreateDocument(BackupViewModel.MIME_TYPE)) { uri ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        requireActivity().contentResolver.openOutputStream(it)!!.bufferedWriter()
                            .use { writer ->
                                writer.write(contentToExport)
                            }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }
        }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val fileName = requireContext().contentResolver.query(uri, null, null, null, null)
                    ?.use { cursor ->
                        cursor.moveToFirst()
                        cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                            .let(cursor::getString)
                    }
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                viewModel.onFileSelectedForImport(fileName, inputStream)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = LayoutBackupBinding.bind(view)

        binding.actionExport.setOnClickListener {
            viewModel.onExportClicked(
                binding.backupConfigurations.isChecked,
                binding.backupRules.isChecked,
                binding.backupSettings.isChecked,
            )
        }

        binding.actionShare.setOnClickListener {
            viewModel.onShareClicked(
                binding.backupConfigurations.isChecked,
                binding.backupRules.isChecked,
                binding.backupSettings.isChecked,
            )
        }

        binding.actionImportFile.setOnClickListener {
            // Let the launcher handle the MIME type
            importFile.launch("*/*")
        }

        lifecycleScope.launch {
            observeViewModel()
        }
    }

    private suspend fun observeViewModel() {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is BackupFragmentEvent.RequestExport -> {
                            contentToExport = event.content
                            exportSettings.launch(event.fileName)
                        }

                        is BackupFragmentEvent.RequestShare -> {
                            handleShareRequest(event.fileName, event.content)
                        }

                        is BackupFragmentEvent.ShowImportDialog -> {
                            showImportDialog(
                                event.contentJson,
                                event.hasProfiles,
                                event.hasRules,
                                event.hasSettings,
                            )
                        }

                        is BackupFragmentEvent.RestartApp -> {
                            ProcessPhoenix.triggerRebirth(
                                requireContext(), Intent(requireContext(), MainActivity::class.java)
                            )
                        }

                        is BackupFragmentEvent.ShowSnackbar -> {
                            snackbar(event.message).show()
                        }

                        is BackupFragmentEvent.ShowError -> {
                            alert(event.message).show()
                        }
                    }
                }
            }

            launch {
                viewModel.isImporting.collect { isImporting ->
                    if (isImporting) showProgressDialog() else hideProgressDialog()
                }
            }
        }
    }

    private fun handleShareRequest(fileName: String, content: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheFile = File(requireContext().cacheDir, fileName)
            cacheFile.writeText(content)
            val fileUri = FileProvider.getUriForFile(
                requireContext(),
                BuildConfig.APPLICATION_ID + ".cache",
                cacheFile,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = BackupViewModel.MIME_TYPE
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_STREAM, fileUri)
            }

            onMainDispatcher {
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(androidx.appcompat.R.string.abc_shareactionprovider_share_with),
                    )
                )
            }
        }
    }

    private fun showImportDialog(
        contentJson: String,
        hasProfiles: Boolean,
        hasRules: Boolean,
        hasSettings: Boolean
    ) {
        val importBinding = LayoutImportBinding.inflate(layoutInflater)
        importBinding.backupConfigurations.isVisible = hasProfiles
        importBinding.backupRules.isVisible = hasRules
        importBinding.backupSettings.isVisible = hasSettings

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_import)
            .setView(importBinding.root)
            .setPositiveButton(R.string.backup_import) { _, _ ->
                viewModel.onImportConfirmed(
                    contentJson,
                    importBinding.backupConfigurations.isChecked,
                    importBinding.backupRules.isChecked,
                    importBinding.backupSettings.isChecked,
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showProgressDialog() {
        if (progressDialog == null) {
            val binding = LayoutProgressBinding.inflate(layoutInflater)
            binding.content.text = getString(R.string.backup_importing)
            progressDialog = AlertDialog.Builder(requireContext())
                .setView(binding.root)
                .setCancelable(false)
                .show()
        }
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onDestroyView() {
        // Ensure dialog is dismissed to prevent leaks
        hideProgressDialog()
        super.onDestroyView()
    }
}