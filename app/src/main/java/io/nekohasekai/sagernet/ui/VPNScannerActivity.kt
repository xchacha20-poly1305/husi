package io.nekohasekai.sagernet.ui

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutVpnScannerBinding
import io.nekohasekai.sagernet.databinding.ViewVpnAppItemBinding
import io.nekohasekai.sagernet.ktx.toStringIterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libcore.Libcore
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.roundToInt

class VPNScannerActivity : ThemedActivity() {

    private var binding: LayoutVpnScannerBinding? = null
    private var adapter: Adapter? = null
    private val appInfoList = mutableListOf<AppInfo>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = LayoutVpnScannerBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.scan_vpn_app)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        binding.scanVPNResult.adapter = Adapter().also {
            adapter = it
        }
        binding.scanVPNResult.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch(Dispatchers.IO) {
            scanVPN()
        }
    }

    class VPNType(
        val appType: String?,
        val coreType: VPNCoreType?,
    )

    class VPNCoreType(
        val coreType: String,
        val corePath: String,
        val goVersion: String
    )

    class AppInfo(
        val packageInfo: PackageInfo,
        val vpnType: VPNType,
    )

    inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ViewVpnAppItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun getItemCount(): Int {
            return appInfoList.size
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(appInfoList[position])
        }
    }

    class Holder(
        private val binding: ViewVpnAppItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(element: AppInfo) {
            binding.appIcon.setImageDrawable(element.packageInfo.applicationInfo.loadIcon(binding.root.context.packageManager))
            binding.appName.text =
                element.packageInfo.applicationInfo.loadLabel(binding.root.context.packageManager)
            binding.packageName.text = element.packageInfo.packageName
            val appType = element.vpnType.appType
            if (appType != null) {
                binding.appTypeText.text = element.vpnType.appType
            } else {
                binding.appTypeText.setText(R.string.vpn_app_type_other)
            }
            val coreType = element.vpnType.coreType?.coreType
            if (coreType != null) {
                binding.coreTypeText.text = element.vpnType.coreType.coreType
            } else {
                binding.coreTypeText.setText(R.string.vpn_core_type_unknown)
            }
            val corePath = element.vpnType.coreType?.corePath.takeIf { !it.isNullOrBlank() }
            if (corePath != null) {
                binding.corePathLayout.isVisible = true
                binding.corePathText.text = corePath
            } else {
                binding.corePathLayout.isVisible = false
            }

            val goVersion = element.vpnType.coreType?.goVersion.takeIf { !it.isNullOrBlank() }
            if (goVersion != null) {
                binding.goVersionLayout.isVisible = true
                binding.goVersionText.text = goVersion
            } else {
                binding.goVersionLayout.isVisible = false
            }
        }
    }

    private suspend fun scanVPN() {
        val adapter = adapter ?: return
        val binding = binding ?: return
        val flag =
            PackageManager.GET_SERVICES or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES
            }
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flag.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flag)
        }
        val vpnAppList =
            installedPackages.filter {
                it.services?.any { it.permission == Manifest.permission.BIND_VPN_SERVICE } ?: false
            }
        for ((index, packageInfo) in vpnAppList.withIndex()) {
            val appType = runCatching { getVPNAppType(packageInfo) }.getOrNull()
            val coreType = runCatching { getVPNCoreType(packageInfo) }.getOrNull()
            appInfoList.add(AppInfo(packageInfo, VPNType(appType, coreType)))
            withContext(Dispatchers.Main) {
                adapter.notifyItemInserted(index)
                binding.scanVPNResult.scrollToPosition(index)
                binding.scanVPNProgress.setProgressCompat(
                    (((index + 1).toFloat() / vpnAppList.size.toFloat()) * 100).roundToInt(),
                    true
                )
            }
            System.gc()
        }
        withContext(Dispatchers.Main) {
            binding.scanVPNProgress.isVisible = false
        }
    }

    companion object {

        private val v2rayNGClasses = listOf(
            "com.v2ray.ang",
            ".dto.V2rayConfig",
            ".service.V2RayVpnService",
        )

        private val clashForAndroidClasses = listOf(
            "com.github.kr328.clash",
            ".core.Clash",
            ".service.TunService",
        )

        private val sfaClasses = listOf(
            "io.nekohasekai.sfa"
        )

        private val legacySagerNetClasses = listOf(
            "io.nekohasekai.sagernet",
            ".fmt.ConfigBuilder"
        )

        private val shadowsocksAndroidClasses = listOf(
            "com.github.shadowsocks",
            ".bg.VpnService",
            "GuardedProcessPool"
        )
    }

    private fun getVPNAppType(packageInfo: PackageInfo): String? {
        ZipFile(File(packageInfo.applicationInfo.publicSourceDir)).use { packageFile ->
            for (packageEntry in packageFile.entries()) {
                if (!(packageEntry.name.startsWith("classes") && packageEntry.name.endsWith(
                        ".dex"
                    ))
                ) {
                    continue
                }
                if (packageEntry.size > 15000000) {
                    continue
                }
                val input = packageFile.getInputStream(packageEntry).buffered()
                val dexFile = try {
                    DexBackedDexFile.fromInputStream(null, input)
                } catch (e: Exception) {
                    Log.e("VPNScanActivity", "Failed to read dex file", e)
                    continue
                }
                for (clazz in dexFile.classes) {
                    val clazzName = clazz.type.substring(1, clazz.type.length - 1)
                        .replace("/", ".")
                        .replace("$", ".")
                    for (v2rayNGClass in v2rayNGClasses) {
                        if (clazzName.contains(v2rayNGClass)) {
                            return "V2RayNG"
                        }
                    }
                    for (clashForAndroidClass in clashForAndroidClasses) {
                        if (clazzName.contains(clashForAndroidClass)) {
                            return "ClashForAndroid"
                        }
                    }
                    for (sfaClass in sfaClasses) {
                        if (clazzName.contains(sfaClass)) {
                            return "sing-box"
                        }
                    }
                    for (legacySagerNetClass in legacySagerNetClasses) {
                        if (clazzName.contains(legacySagerNetClass)) {
                            return "LegacySagerNet"
                        }
                    }
                    for (shadowsocksAndroidClass in shadowsocksAndroidClasses) {
                        if (clazzName.contains(shadowsocksAndroidClass)) {
                            return "shadowsocks-android"
                        }
                    }
                }
            }
            return null
        }
    }

    private fun getVPNCoreType(packageInfo: PackageInfo): VPNCoreType? {
        val packageFiles = mutableListOf(packageInfo.applicationInfo.publicSourceDir)
        packageInfo.applicationInfo.splitPublicSourceDirs?.also {
            packageFiles.addAll(it)
        }
        val vpnType = try {
            Libcore.readAndroidVPNType(packageFiles.toStringIterator())
        } catch (ignored: Exception) {
            return null
        }
        return VPNCoreType(vpnType.coreType, vpnType.corePath, vpnType.goVersion)
    }
}