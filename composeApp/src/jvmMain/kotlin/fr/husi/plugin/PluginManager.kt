package fr.husi.plugin

import java.io.FileNotFoundException

class PluginNotFoundException(val plugin: String) : FileNotFoundException(plugin)

class CustomPluginNotApprovedException(val pluginId: String) :
    IllegalStateException("Custom plugin not approved: $pluginId")

data class PluginInitResult(
    val path: String,
)

expect object PluginManager {

    @Throws(Throwable::class)
    fun init(pluginId: String): PluginInitResult?

    @Throws(Throwable::class)
    fun resolveCustomPluginExecutable(pluginId: String, executablePath: String): String
}
