package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAboutBinding
import io.nekohasekai.sagernet.databinding.ViewAboutCardBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import libcore.Libcore

@OptIn(ExperimentalMaterial3Api::class)
class AboutFragment : OnKeyDownFragment(R.layout.layout_about) {

    private lateinit var binding: LayoutAboutBinding
    private val viewModel by viewModels<AboutFragmentViewModel>()
    private lateinit var adapter: AboutAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutAboutBinding.bind(view)
        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            Mdc3Theme {
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_about)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            (requireActivity() as MainActivity).binding
                                .drawerLayout.openDrawer(GravityCompat.START)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.menu),
                            )
                        }
                    },
                )
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutAbout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }

        binding.aboutRecycler.adapter = AboutAdapter { message ->
            snackbar(message).show()
        }.also {
            adapter = it
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }

        binding.aboutGithub.setOnClickListener { view ->
            view.context.launchCustomTab("https://github.com/xchacha20-poly1305/husi")
        }
        binding.aboutTranslate.setOnClickListener { view ->
            view.context.launchCustomTab("https://hosted.weblate.org/projects/husi/husi/")
        }

        binding.license.text = LICENSE
        Linkify.addLinks(binding.license, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
    }

    private fun handleUiState(state: AboutFragmentUiState) {
        val context = requireContext()
        val shouldRequestBatteryOptimizations =
            !(requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        val cards = ArrayList<AboutCard>(
            2 // App version and SingBox version
                    + state.plugins.size // Plugins
                    + if (shouldRequestBatteryOptimizations) 1 else 0 // Battery optimization
                    + 1 // Sponsor
        ).apply {
            add(AboutCard.AppVersion)
            add(AboutCard.SingBoxVersion)
            state.plugins.forEach { plugin ->
                add(AboutCard.Plugin(plugin))
            }
            if (shouldRequestBatteryOptimizations) {
                add(AboutCard.BatteryOptimization(requestIgnoreBatteryOptimizations))
            }
            add(AboutCard.Sponsor)
        }
        adapter.submitList(cards)
    }

    val requestIgnoreBatteryOptimizations = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) lifecycleScope.launch {
            delay(1000) // Wait for updating battery optimization config
            handleUiState(viewModel.uiState.value)
        }
    }

    private sealed interface AboutCard {
        object AppVersion : AboutCard
        object SingBoxVersion : AboutCard
        data class Plugin(val plugin: AboutPlugin) : AboutCard
        class BatteryOptimization(val launcher: ActivityResultLauncher<Intent>) : AboutCard {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                return true
            }

            override fun hashCode(): Int {
                return javaClass.hashCode()
            }
        }

        object Sponsor : AboutCard
    }

    private class AboutAdapter(val snackbar: (CharSequence) -> Unit) :
        ListAdapter<AboutCard, AboutPluginHolder>(AboutCardDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AboutPluginHolder {
            return AboutPluginHolder(
                ViewAboutCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun onBindViewHolder(holder: AboutPluginHolder, position: Int) {
            when (val item = getItem(position)) {
                is AboutCard.AppVersion -> holder.bindAppVersion()
                is AboutCard.SingBoxVersion -> holder.bindSingBoxVersion()
                is AboutCard.Plugin -> holder.bindPlugin(item.plugin)
                is AboutCard.BatteryOptimization -> holder.bindBatteryOptimization(item.launcher)
                is AboutCard.Sponsor -> holder.bindSponsor(snackbar)
            }
        }

    }

    private class AboutCardDiffCallback : DiffUtil.ItemCallback<AboutCard>() {
        override fun areItemsTheSame(old: AboutCard, new: AboutCard): Boolean {
            return when (old) {
                is AboutCard.AppVersion -> new is AboutCard.AppVersion
                is AboutCard.SingBoxVersion -> new is AboutCard.SingBoxVersion
                is AboutCard.Plugin -> new is AboutCard.Plugin && old.plugin.id == new.plugin.id
                is AboutCard.BatteryOptimization -> new is AboutCard.BatteryOptimization
                is AboutCard.Sponsor -> new is AboutCard.Sponsor
            }
        }

        override fun areContentsTheSame(old: AboutCard, new: AboutCard): Boolean {
            return true
        }
    }

    private class AboutPluginHolder(private val binding: ViewAboutCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindAppVersion() {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_sanitizer_24)
            binding.aboutCardTitle.setText(R.string.app_name)

            var displayVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            if (BuildConfig.DEBUG) {
                displayVersion += " DEBUG"
            }
            binding.aboutCardDescription.isVisible = true
            binding.aboutCardDescription.text = displayVersion

            binding.root.setOnClickListener { view ->
                view.context.launchCustomTab(
                    if (Libcore.isPreRelease(BuildConfig.VERSION_NAME)) {
                        "https://github.com/xchacha20-poly1305/husi/releases"
                    } else {
                        "https://github.com/xchacha20-poly1305/husi/releases/latest"
                    }
                )
            }
            binding.root.setOnLongClickListener(null)
        }

        fun bindSingBoxVersion() {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_layers_24)
            binding.aboutCardTitle.text = binding.aboutCardTitle.context.getString(
                R.string.version_x,
                "sing-box",
            )
            binding.aboutCardDescription.isVisible = true
            binding.aboutCardDescription.text = Libcore.version()
            binding.root.setOnClickListener { view ->
                view.context.launchCustomTab("https://github.com/SagerNet/sing-box")
            }
            binding.root.setOnLongClickListener(null)
        }

        fun bindPlugin(plugin: AboutPlugin) {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_nfc_24)
            binding.aboutCardTitle.text = binding.aboutCardTitle.context.getString(
                R.string.version_x,
                plugin.id,
            ) + " (${plugin.provider})"
            binding.aboutCardDescription.isVisible = true
            binding.aboutCardDescription.text = "v${plugin.version}"
            binding.root.setOnClickListener { view ->
                view.context.startActivity(Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", plugin.packageName, null)
                })
            }
            plugin.entry?.let {
                binding.root.setOnLongClickListener { view ->
                    view.context.launchCustomTab(it.downloadSource.downloadLink)
                    true
                }
            }
        }

        fun bindBatteryOptimization(launcher: ActivityResultLauncher<Intent>) {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_running_with_errors_24)
            binding.aboutCardTitle.setText(R.string.ignore_battery_optimizations)
            binding.aboutCardDescription.isVisible = true
            binding.aboutCardDescription.setText(R.string.ignore_battery_optimizations_sum)
            binding.root.setOnClickListener { view ->
                launcher.launch(Intent().apply {
                    @SuppressLint("BatteryLife") // We don't care about Google Play Police
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = "package:${view.context.packageName}".toUri()
                })
            }
            binding.root.setOnLongClickListener(null)
        }

        fun bindSponsor(snackbar: (CharSequence) -> Unit) {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_card_giftcard_24)
            binding.aboutCardTitle.setText(R.string.sekai)
            binding.aboutCardDescription.isVisible = false
            binding.root.setOnClickListener { view ->
                view.context.launchCustomTab("https://sekai.icu/sponsor")
            }
            binding.root.setOnLongClickListener { view ->
                val isExpert = !DataStore.isExpert
                DataStore.isExpert = isExpert
                snackbar("isExpert: $isExpert")
                true
            }
        }
    }

}
