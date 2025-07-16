package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.util.Linkify
import android.view.View
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutAboutBinding
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import io.nekohasekai.sagernet.plugin.Plugins
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.delay
import libcore.Libcore

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    lateinit var binding: LayoutAboutBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutAboutBinding.bind(view)

        toolbar.setTitle(R.string.menu_about)
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

        if (savedInstanceState == null) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.about_fragment_holder, AboutContent())
                .commit()
        }

        binding.license.text = LICENSE
        Linkify.addLinks(binding.license, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
    }

    class AboutContent : MaterialAboutFragment() {

        val requestIgnoreBatteryOptimizations = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (resultCode, _) ->
            if (resultCode == Activity.RESULT_OK) runOnDefaultDispatcher {
                delay(1000) // Wait for updating battery optimization config
                onMainDispatcher {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.about_fragment_holder, AboutContent())
                        .commitAllowingStateLoss()
                }
            }
        }

        override fun getMaterialAboutList(activityContext: Context): MaterialAboutList {

            var displayVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            if (BuildConfig.DEBUG) {
                displayVersion += " DEBUG"
            }

            return MaterialAboutList.Builder()
                .addCard(
                    MaterialAboutCard.Builder()
                        .outline(false)
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.ic_baseline_update_24)
                                .text(R.string.app_version)
                                .subText(displayVersion)
                                .setOnClickAction {
                                    activityContext.launchCustomTab(
                                        if (Libcore.isPreRelease(BuildConfig.VERSION_NAME)) {
                                            "https://github.com/xchacha20-poly1305/husi/releases"
                                        } else {
                                            "https://github.com/xchacha20-poly1305/husi/releases/latest"
                                        }
                                    )
                                }
                                .build())
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.ic_baseline_layers_24)
                                .text(activityContext.getString(R.string.version_x, "sing-box"))
                                .subText(Libcore.version())
                                .setOnClickAction {
                                    activityContext.launchCustomTab(
                                        "https://github.com/SagerNet/sing-box"
                                    )
                                }
                                .build())
                        .apply {
                            PackageCache.awaitLoadSync()
                            for ((_, pkg) in PackageCache.installedPluginPackages) {
                                try {
                                    val pluginId =
                                        pkg.providers!![0].loadString(Plugins.METADATA_KEY_ID)
                                    if (pluginId.isNullOrBlank()) continue
                                    addItem(
                                        MaterialAboutActionItem.Builder()
                                            .icon(R.drawable.ic_baseline_nfc_24)
                                            .text(
                                                activityContext.getString(
                                                    R.string.version_x,
                                                    pluginId
                                                ) + " (${Plugins.displayExeProvider(pkg.packageName)})"
                                            )
                                            .subText("v" + pkg.versionName)
                                            .setOnClickAction {
                                                activityContext.startActivity(Intent().apply {
                                                    action =
                                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                                    data = Uri.fromParts(
                                                        "package", pkg.packageName, null
                                                    )
                                                })
                                            }
                                            .apply {
                                                val id =
                                                    pkg.providers!![0].loadString(Plugins.METADATA_KEY_ID)
                                                PluginEntry.find(id)?.let { entry ->
                                                    setOnLongClickAction {
                                                        activityContext.launchCustomTab(
                                                            entry.downloadSource.downloadLink
                                                        )
                                                    }
                                                }
                                            }
                                            .build())
                                } catch (e: Exception) {
                                    Logs.w(e)
                                }
                            }
                        }
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val pm =
                                    activityContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                                if (!pm.isIgnoringBatteryOptimizations(activityContext.packageName)) {
                                    addItem(
                                        MaterialAboutActionItem.Builder()
                                            .icon(R.drawable.ic_baseline_running_with_errors_24)
                                            .text(R.string.ignore_battery_optimizations)
                                            .subText(R.string.ignore_battery_optimizations_sum)
                                            .setOnClickAction {
                                                requestIgnoreBatteryOptimizations.launch(
                                                    Intent(
                                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                        "package:${activityContext.packageName}".toUri()
                                                    )
                                                )
                                            }
                                            .build())
                                }
                            }
                            addItem(
                                MaterialAboutActionItem.Builder()
                                    .icon(R.drawable.ic_baseline_card_giftcard_24)
                                    .text(R.string.sekai)
                                    .setOnClickAction {
                                        activityContext.launchCustomTab("https://sekai.icu/sponsors/")
                                    }
                                    .build()
                            )
                        }
                        .build())
                .addCard(
                    MaterialAboutCard.Builder()
                        .outline(false)
                        .title(R.string.project)
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.ic_baseline_sanitizer_24)
                                .text(R.string.github)
                                .setOnClickAction {
                                    activityContext.launchCustomTab(
                                        "https://github.com/xchacha20-poly1305/husi"

                                    )
                                }
                                .build())
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.baseline_translate_24)
                                .text(R.string.translate_platform)
                                .setOnClickAction {
                                    activityContext.launchCustomTab(
                                        "https://hosted.weblate.org/projects/husi/husi/"

                                    )
                                }
                                .build())
                        .build())
                .build()

        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<RecyclerView>(com.danielstone.materialaboutlibrary.R.id.mal_recyclerview)
                .apply {
                    overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                }
        }

    }

}
