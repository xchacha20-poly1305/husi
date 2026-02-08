package io.nekohasekai.sagernet.ui.tools

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.toStringIterator
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import libcore.Libcore
import java.io.File
import java.util.zip.ZipFile
import kotlin.collections.iterator

@Immutable
internal data class VPNScannerUiState(
    val appInfos: List<AppInfo> = emptyList(),
    val progress: Float? = null,
)

@Immutable
internal data class AppInfo(
    val packageInfo: PackageInfo,
    val label: String,
    val icon: Drawable,
    val vpnType: VPNType,
)

@Immutable
internal data class VPNType(
    val appType: String?,
    val coreType: VPNCoreType?,
)

@Immutable
internal data class VPNCoreType(
    val coreType: String,
    val corePath: String,
    val goVersion: String,
)

@Stable
internal class VPNScannerActivityViewModel : ViewModel() {
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
            "io.nekohasekai.sfa",
        )

        private val sagerNetClasses = listOf(
            "io.nekohasekai.sagernet",
            ".fmt.ConfigBuilder",
        )

        private val shadowsocksAndroidClasses = listOf(
            "com.github.shadowsocks",
            ".bg.VpnService",
            "GuardedProcessPool",
        )
    }

    private val _uiState = MutableStateFlow(VPNScannerUiState())
    val uiState = _uiState.asStateFlow()

    fun scanVPN() {
        viewModelScope.launch(Dispatchers.IO) {
            scanVPN0()
        }
    }

    /**
     * Scroll in the LazyColumn will load icons frequently, so we cache them here.
     */
    private val iconCache = mutableMapOf<String, Drawable>()
    private fun loadIcon(packageManager: PackageManager, packageInfo: PackageInfo): Drawable {
        return iconCache.getOrPut(packageInfo.packageName) {
            packageInfo.applicationInfo!!.loadIcon(packageManager)
        }
    }

    private suspend fun scanVPN0() {
        _uiState.emit(_uiState.value.copy(appInfos = emptyList(), progress = 0f))
        val packageManager = repo.packageManager
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
            packageManager.getInstalledPackages(flag)
        }
        val vpnAppList =
            installedPackages.filter {
                it.services?.any { serviceInfo ->
                    serviceInfo.permission == Manifest.permission.BIND_VPN_SERVICE
                } ?: false
            }

        val foundApps = mutableListOf<AppInfo>()
        for ((i, packageInfo) in vpnAppList.withIndex()) {
            val appType = runCatching { getVPNAppType(packageInfo) }.getOrNull()
            val coreType = runCatching { getVPNCoreType(packageInfo) }.getOrNull()

            val appInfo = AppInfo(
                packageInfo = packageInfo,
                label = PackageCache.loadLabel(packageManager, packageInfo.packageName),
                icon = loadIcon(packageManager, packageInfo),
                vpnType = VPNType(
                    appType = appType,
                    coreType = coreType,
                ),
            )
            foundApps.add(appInfo)
            val progress = ((i + 1).toDouble() / vpnAppList.size.toDouble()).toFloat()
            _uiState.emit(_uiState.value.copy(appInfos = foundApps.toList(), progress = progress))

            System.gc()
        }

        _uiState.emit(_uiState.value.copy(progress = null))
    }

    private fun getVPNAppType(packageInfo: PackageInfo): String? {
        ZipFile(File(packageInfo.applicationInfo!!.publicSourceDir)).use { packageFile ->
            var type: String? = null
            for (packageEntry in packageFile.entries()) {
                if (
                    !(packageEntry.name.startsWith("classes") &&
                            packageEntry.name.endsWith(".dex"))
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
                    Logs.e("Read dex file", e)
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
                    for (sagerNetClass in sagerNetClasses) {
                        if (clazzName.contains(sagerNetClass)) {
                            return "SagerNet"
                        }
                    }
                    for (shadowsocksAndroidClass in shadowsocksAndroidClasses) {
                        if (clazzName.contains(shadowsocksAndroidClass)) {
                            // May be SagerNet, too. Scan more to confirm.
                            type = "shadowsocks-android"
                            continue
                        }
                    }
                }
            }
            return type
        }
    }

    private fun getVPNCoreType(packageInfo: PackageInfo): VPNCoreType? {
        val packageFiles = mutableListOf(packageInfo.applicationInfo!!.publicSourceDir)
        packageInfo.applicationInfo!!.splitPublicSourceDirs?.also {
            packageFiles.addAll(it)
        }
        val vpnType = try {
            Libcore.readAndroidVPNType(packageFiles.let { it.toStringIterator(it.size) })
        } catch (_: Exception) {
            return null
        }
        return VPNCoreType(vpnType.coreType, vpnType.corePath, vpnType.goVersion)
    }
}