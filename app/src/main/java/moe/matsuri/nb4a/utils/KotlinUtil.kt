package moe.matsuri.nb4a.utils

import androidx.appcompat.widget.SearchView
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.mapX
import org.json.JSONArray
import java.io.Closeable
import java.io.File

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

// List

fun String.listByLineOrComma(): List<String> {
    return this.split(",", "\n").mapX { it.trim() }.filter { it.isNotEmpty() }
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

// View

fun SearchView.setOnFocusCancel(callback: ((hasFocus: Boolean)->Unit)? = null) {
    setOnQueryTextFocusChangeListener { _, hasFocus ->
        if (!hasFocus) {
            onActionViewCollapsed()
            clearFocus()
        }
        callback?.invoke(hasFocus)
    }
}