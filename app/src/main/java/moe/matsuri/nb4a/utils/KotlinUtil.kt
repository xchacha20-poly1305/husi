package moe.matsuri.nb4a.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.forEach
import org.json.JSONArray
import java.io.Closeable
import java.io.File

// SagerNet Class

fun SagerNet.cleanWebview() {
    var pathToClean = "app_webview"
    if (isBgProcess) pathToClean += "_$process"
    try {
        val dataDir = filesDir.parentFile!!
        File(dataDir, "$pathToClean/BrowserMetrics").recreate(true)
        File(dataDir, "$pathToClean/BrowserMetrics-spare.pma").recreate(false)
    } catch (e: Exception) {
        Logs.e(e)
    }
}

fun File.recreate(dir: Boolean) {
    if (parentFile?.isDirectory != true) return
    if (dir && !isFile) {
        if (exists()) deleteRecursively()
        createNewFile()
    } else if (!dir && !isDirectory) {
        if (exists()) delete()
        mkdir()
    }
}

// Context utils

@SuppressLint("DiscouragedApi")
fun Context.getDrawableByName(name: String?): Drawable? {
    val resourceId: Int = resources.getIdentifier(name, "drawable", packageName)
    return AppCompatResources.getDrawable(this, resourceId)
}

// List

fun String.listByLineOrComma(): List<String> {
    return this.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
}

// Address

// blur used to make server address blurred.
fun String.blur(): String {
    val l = this.length
    return if (l < 20) {
        val halfLength = this.length / 2
        this.substring(0, halfLength) + "*".repeat(this.length - halfLength)
    } else {
        this.substring(0, 15) + "*".repeat(3)
    }
}


fun Closeable.closeQuietly() {
    try {
        close()
    } catch (rethrown: RuntimeException) {
        throw rethrown
    } catch (_: Exception) {
    }
}


// ProxyEntity

fun ProxyEntity.findGroup(): ProxyGroup? {
    return SagerDatabase.groupDao.getById(groupId)
}

// JSON

fun <T> JSONArray.toList(): MutableList<T> {
    return this.toList { any ->
        @Suppress("UNCHECKED_CAST")
        any as T
    }
}

fun <T> JSONArray.toList(turn: (Any) -> T): MutableList<T> {
    val list = mutableListOf<T>()
    this.forEach { _, any ->
        list.add(turn(any))
    }
    return list
}
