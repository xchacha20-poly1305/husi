package io.nekohasekai.sagernet.utils

import android.content.pm.PackageManager
import android.os.Build
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import io.nekohasekai.sagernet.ktx.Logs
import java.io.File
import java.util.zip.ZipFile
import kotlin.collections.iterator

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

    fun isChinaApp(packageName: String, packageManager: PackageManager): Boolean {
        skipPrefixList.forEach {
            if (packageName == it || packageName.startsWith("$it.")) return false
        }

        val packageManagerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        }
        if (packageName.matches(chinaAppRegex)) {
            Logs.d("Match package name: $packageName")
            return true
        }
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(packageManagerFlags.toLong())
                )
            } else {
                packageManager.getPackageInfo(packageName, packageManagerFlags)
            }
            packageInfo.services?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match service ${it.name} in $packageName")
                    return true
                }
            }
            packageInfo.activities?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match activity ${it.name} in $packageName")
                    return true
                }
            }
            packageInfo.receivers?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match receiver ${it.name} in $packageName")
                    return true
                }
            }
            packageInfo.providers?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Logs.d("Match provider ${it.name} in $packageName")
                    return true
                }
            }
            ZipFile(File(packageInfo.applicationInfo!!.publicSourceDir)).use {
                for (packageEntry in it.entries()) {
                    if (packageEntry.name.startsWith("firebase-")) return false
                }
                for (packageEntry in it.entries()) {
                    if (!(packageEntry.name.startsWith("classes") && packageEntry.name.endsWith(
                            ".dex"
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
        } catch (e: Exception) {
            Logs.e("Error scanning package $packageName", e)
        }
        return false
    }
}