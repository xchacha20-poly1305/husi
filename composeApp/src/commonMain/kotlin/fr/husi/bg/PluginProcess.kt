package fr.husi.bg

import com.google.protobuf.ByteString
import fr.husi.database.DataStore
import fr.husi.fmt.ConfigBuildResult
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.hysteria.buildHysteriaConfig
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.fmt.juicity.buildJuicityConfig
import fr.husi.fmt.mieru.MieruBean
import fr.husi.fmt.mieru.buildMieruConfig
import fr.husi.fmt.naive.NaiveBean
import fr.husi.fmt.naive.buildNaiveConfig
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.fmt.shadowquic.buildShadowQUICConfig
import fr.husi.libcore.Libcore
import fr.husi.platform.PlatformInfo
import fr.husi.plugin.PluginManager
import fr.husi.proto.v1.PluginProcessSpec
import fr.husi.proto.v1.pluginFile
import fr.husi.proto.v1.pluginProcessSpec
import fr.husi.repository.resolveRepository
import java.io.File

fun initPlugins(
    config: ConfigBuildResult,
    isVPN: Boolean,
    cacheFiles: MutableList<File>,
): Map<Int, Pair<Int, String>> {
    val repository = resolveRepository()
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    val logLevel = DataStore.logLevel
    val shouldProtect = isVPN && PlatformInfo.isAndroid
    for ((chain) in config.externalIndex) {
        chain.entries.forEach { (port, profile) ->
            when (val bean = profile.requireBean()) {
                is MieruBean -> {
                    PluginManager.init("mieru-plugin")
                    pluginConfigs[port] = profile.type to bean.buildMieruConfig(port, logLevel)
                }

                is NaiveBean -> {
                    PluginManager.init("naive-plugin")
                    pluginConfigs[port] = profile.type to bean.buildNaiveConfig(port)
                }

                is HysteriaBean -> {
                    when (bean.protocolVersion) {
                        HysteriaBean.PROTOCOL_VERSION_1 -> PluginManager.init("hysteria-plugin")
                        HysteriaBean.PROTOCOL_VERSION_2 -> PluginManager.init("hysteria2-plugin")
                    }
                    pluginConfigs[port] =
                        profile.type to bean.buildHysteriaConfig(port, shouldProtect) { type ->
                            File(repository.cacheDir, "hysteria_${System.currentTimeMillis()}.$type").also {
                                it.parentFile?.mkdirs()
                                cacheFiles.add(it)
                            }
                        }
                }

                is JuicityBean -> {
                    PluginManager.init("juicity-plugin")
                    pluginConfigs[port] = profile.type to bean.buildJuicityConfig(port, shouldProtect)
                }

                is ShadowQUICBean -> {
                    PluginManager.init("shadowquic-plugin")
                    pluginConfigs[port] =
                        profile.type to bean.buildShadowQUICConfig(port, shouldProtect, logLevel) { type ->
                            File(repository.cacheDir, "shadowquic_${System.currentTimeMillis()}.$type").also {
                                it.parentFile?.mkdirs()
                                cacheFiles.add(it)
                            }
                        }
                }
            }
        }
    }
    return pluginConfigs
}

fun buildPluginSpecs(
    config: ConfigBuildResult,
    pluginConfigs: Map<Int, Pair<Int, String>>,
    isVPN: Boolean,
): List<PluginProcessSpec> {
    val repository = resolveRepository()
    val shouldProtect = isVPN && PlatformInfo.isAndroid
    val specs = ArrayList<PluginProcessSpec>()

    val sharedEnv = linkedMapOf<String, String>()
    repository.externalAssetsDir.resolve(Libcore.PluginCaFile)
        .takeIf { it.isFile }
        ?.absolutePath
        ?.let { sharedEnv["SSL_CERT_FILE"] = it }

    for ((chain) in config.externalIndex) {
        chain.entries.forEach { (port, profile) ->
            val bean = profile.requireBean()
            val (_, cfg) = pluginConfigs[port] ?: return@forEach
            val env = linkedMapOf<String, String>().apply { putAll(sharedEnv) }

            val spec = when (bean) {
                is MieruBean -> {
                    val configName = "mieru_$port.json"
                    if (shouldProtect) {
                        env["MIERU_PROTECT_PATH"] = Libcore.ProtectPath
                    }
                    env["MIERU_CONFIG_JSON_FILE"] = pluginFileToken(configName)
                    pluginProcessSpec {
                        name = "mieru-plugin"
                        command.add(PluginManager.init("mieru-plugin")!!.path)
                        command.add("run")
                        environment.putAll(env)
                        files.add(pluginConfigFile(configName, cfg))
                    }
                }

                is NaiveBean -> {
                    val configName = "naive_$port.json"
                    pluginProcessSpec {
                        name = "naive-plugin"
                        command.add(PluginManager.init("naive-plugin")!!.path)
                        command.add(pluginFileToken(configName))
                        environment.putAll(env)
                        files.add(pluginConfigFile(configName, cfg))
                    }
                }

                is HysteriaBean -> {
                    val configName = "hysteria_$port.json"
                    env["HYSTERIA_DISABLE_UPDATE_CHECK"] = "1"
                    val pluginId = if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                        "hysteria-plugin"
                    } else {
                        "hysteria2-plugin"
                    }
                    val executable = PluginManager.init(pluginId)!!.path
                    val commands = if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                        mutableListOf(
                            executable,
                            "client",
                            "--no-check",
                            "--config",
                            pluginFileToken(configName),
                            "--log-level",
                            if (DataStore.logLevel > 0) "trace" else "warn",
                        )
                    } else {
                        mutableListOf(
                            executable,
                            "client",
                            "--config",
                            pluginFileToken(configName),
                            "--log-level",
                            if (DataStore.logLevel > 0) "warn" else "error",
                        )
                    }
                    if (PlatformInfo.isAndroid &&
                        bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2 &&
                        bean.protocol == HysteriaBean.PROTOCOL_FAKETCP
                    ) {
                        commands.addAll(0, listOf("su", "-c"))
                    }
                    pluginProcessSpec {
                        name = pluginId
                        command.addAll(commands)
                        environment.putAll(env)
                        files.add(pluginConfigFile(configName, cfg))
                    }
                }

                is JuicityBean -> {
                    val configName = "juicity_$port.json"
                    env["QUIC_GO_DISABLE_GSO"] = "1"
                    pluginProcessSpec {
                        name = "juicity-plugin"
                        command.add(PluginManager.init("juicity-plugin")!!.path)
                        command.add("run")
                        command.add("-c")
                        command.add(pluginFileToken(configName))
                        environment.putAll(env)
                        files.add(pluginConfigFile(configName, cfg))
                    }
                }

                is ShadowQUICBean -> {
                    val configName = "shadowquic_$port.yaml"
                    pluginProcessSpec {
                        name = "shadowquic-plugin"
                        command.add(PluginManager.init("shadowquic-plugin")!!.path)
                        command.add("-c")
                        command.add(pluginFileToken(configName))
                        environment.putAll(env)
                        files.add(pluginConfigFile(configName, cfg))
                    }
                }

                else -> null
            }
            if (spec != null) {
                specs.add(spec)
            }
        }
    }
    return specs
}

private fun pluginFileToken(name: String): String = $$"${file:$$name}"

private fun pluginConfigFile(name: String, content: String) = pluginFile {
    this.name = name
    this.content = ByteString.copyFromUtf8(content)
}
