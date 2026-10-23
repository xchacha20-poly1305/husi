package fr.husi.utils

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import fr.husi.ktx.Logs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException

object AppScanner {
    private val skipPrefixList by lazy {
        listOf(
            "com.google",
            "com.android.chrome",
            "com.android.vending",
            "com.microsoft",
            "com.apple",
            "com.zhiliaoapp.musically", // Banned by China
            "com.android.providers.downloads", // Download manager, which may has Chinese SDK.
        )
    }

    private val chinaAppPrefixList by lazy {
        listOf(
            "com.tencent",
            "com.alibaba",
            "com.umeng",
            "com.qihoo",
            "com.ali",
            "com.alipay",
            "com.amap",
            "com.sina",
            "com.weibo",
            "com.vivo",
            "com.xiaomi",
            "com.huawei",
            "com.taobao",
            "com.secneo",
            "s.h.e.l.l",
            "com.stub",
            "com.kiwisec",
            "com.secshell",
            "com.wrapper",
            "cn.securitystack",
            "com.mogosec",
            "com.secoen",
            "com.netease",
            "com.mx",
            "com.qq.e",
            "com.baidu",
            "com.bytedance",
            "com.bugly",
            "com.miui",
            "com.oppo",
            "com.coloros",
            "com.iqoo",
            "com.meizu",
            "com.gionee",
            "cn.nubia",
            "com.oplus",
            "andes.oplus",
            "com.unionpay",
            "cn.wps",
        )
    }

    private val chinaAppRegex by lazy {
        ("(" + chinaAppPrefixList.joinToString("|").replace(".", "\\.") + ").*").toRegex()
    }

    /**
     * Cancellable between the expensive steps; a single archive or dex read still runs to its end.
     */
    suspend fun isChinaApp(packageName: String, packageManager: PackageManager): Boolean {
        skipPrefixList.forEach {
            if (packageName == it || packageName.startsWith("$it.")) {
                return false
            }
        }

        if (packageName.matches(chinaAppRegex)) {
            Logs.d("Match package name: $packageName")
            return true
        }
        try {
            val applicationInfo = getApplicationInfo(packageManager, packageName)
            currentCoroutineContext().ensureActive()
            val archiveInfo = getComponentArchiveInfo(packageManager, applicationInfo)
            archiveInfo?.services?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match service ${it.name} in $packageName")
                    return true
                }
            }
            archiveInfo?.activities?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match activity ${it.name} in $packageName")
                    return true
                }
            }
            archiveInfo?.receivers?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match receiver ${it.name} in $packageName")
                    return true
                }
            }
            archiveInfo?.providers?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match provider ${it.name} in $packageName")
                    return true
                }
            }
            currentCoroutineContext().ensureActive()
            ZipFile(File(applicationInfo.publicSourceDir)).use {
                for (packageEntry in it.entries()) {
                    if (packageEntry.name.startsWith("firebase-")) return false
                }
                for (packageEntry in it.entries()) {
                    currentCoroutineContext().ensureActive()
                    if (!(packageEntry.name.startsWith("classes") && packageEntry.name.endsWith(
                            ".dex",
                        ))
                    ) {
                        continue
                    }
                    if (packageEntry.size > 15000000) {
                        Logs.d("Confirm $packageName due to large dex file")
                        return true
                    }
                    val input = it.getInputStream(packageEntry).buffered()
                    val dexFile = try {
                        DexBackedDexFile.fromInputStream(null, input)
                    } catch (e: Exception) {
                        Logs.e("Error reading dex file", e)
                        return false
                    }
                    for (clazz in dexFile.classes) {
                        currentCoroutineContext().ensureActive()
                        val clazzName =
                            clazz.type.substring(1, clazz.type.length - 1).replace("/", ".")
                                .replace("$", ".")
                        if (clazzName.matches(chinaAppRegex)) {
                            Logs.d("Match $clazzName in $packageName")
                            return true
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.e("Error scanning package $packageName", e)
        }
        return false
    }

    private fun getApplicationInfo(
        packageManager: PackageManager,
        packageName: String,
    ): ApplicationInfo {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(flags.toLong()),
            )
        } else {
            packageManager.getApplicationInfo(packageName, flags)
        }
    }

    private fun getComponentArchiveInfo(
        packageManager: PackageManager,
        applicationInfo: ApplicationInfo,
    ): PackageInfo? {
        val componentFlags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS
        // Since Android 11, parsing the install directory includes the components of split APKs.
        val archivePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && !applicationInfo.splitPublicSourceDirs.isNullOrEmpty()
        ) {
            File(applicationInfo.publicSourceDir).parent
        } else {
            applicationInfo.publicSourceDir
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                archivePath,
                PackageManager.PackageInfoFlags.of(componentFlags.toLong()),
            )
        } else {
            packageManager.getPackageArchiveInfo(archivePath, componentFlags)
        }
    }
}
