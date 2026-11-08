package fr.husi.plugin

import java.io.FileNotFoundException

class PluginNotFoundException(val plugin: String) : FileNotFoundException(plugin)

data class PluginInitResult(
    val path: String,
)

expect object PluginManager {

    @Throws(Throwable::class)
    fun init(pluginId: String): PluginInitResult?
}
