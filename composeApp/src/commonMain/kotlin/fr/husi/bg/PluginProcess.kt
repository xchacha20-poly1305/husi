package fr.husi.bg

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
import fr.husi.plugin.PluginManager
import fr.husi.repository.repo
import java.io.File

fun initPlugins(
    config: ConfigBuildResult,
    isVPN: Boolean,
    cacheFiles: MutableList<File>,
): Map<Int, Pair<Int, String>> {
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    val logLevel = DataStore.logLevel
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
                        profile.type to bean.buildHysteriaConfig(port, isVPN) { type ->
                            File(repo.cacheDir, "hysteria_${System.currentTimeMillis()}.$type").also {
                                it.parentFile?.mkdirs()
                                cacheFiles.add(it)
                            }
                        }
                }

                is JuicityBean -> {
                    PluginManager.init("juicity-plugin")
                    pluginConfigs[port] = profile.type to bean.buildJuicityConfig(port, isVPN)
                }

                is ShadowQUICBean -> {
                    PluginManager.init("shadowquic-plugin")
                    pluginConfigs[port] =
                        profile.type to bean.buildShadowQUICConfig(port, isVPN, logLevel)
                }
            }
        }
    }
    return pluginConfigs
}

fun launchPlugins(
    config: ConfigBuildResult,
    pluginConfigs: Map<Int, Pair<Int, String>>,
    processes: GuardedProcessPool,
    cacheFiles: MutableList<File>,
) {
    val cacheDir = File(repo.cacheDir, "tmpcfg")
    cacheDir.mkdirs()

    for ((chain) in config.externalIndex) {
        chain.entries.forEach { (port, profile) ->
            val bean = profile.requireBean()
            val (_, cfg) = pluginConfigs[port] ?: return@forEach

            when (bean) {
                is MieruBean -> {
                    val configFile = File(cacheDir, "mieru_${System.currentTimeMillis()}.json")
                    configFile.writeText(cfg)
                    cacheFiles.add(configFile)
                    processes.start(
                        listOf(PluginManager.init("mieru-plugin")!!.path, "run"),
                        mutableMapOf(
                            "MIERU_CONFIG_JSON_FILE" to configFile.absolutePath,
                            "MIERU_PROTECT_PATH" to Libcore.ProtectPath,
                        ),
                    )
                }

                is NaiveBean -> {
                    val configFile = File(cacheDir, "naive_${System.currentTimeMillis()}.json")
                    configFile.writeText(cfg)
                    cacheFiles.add(configFile)
                    processes.start(
                        listOf(
                            PluginManager.init("naive-plugin")!!.path,
                            configFile.absolutePath,
                        ),
                        mutableMapOf(),
                    )
                }

                is HysteriaBean -> {
                    val configFile = File(cacheDir, "hysteria_${System.currentTimeMillis()}.json")
                    configFile.writeText(cfg)
                    cacheFiles.add(configFile)
                    val commands = if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                        mutableListOf(
                            PluginManager.init("hysteria-plugin")!!.path,
                            "client",
                            "--no-check",
                            "--config",
                            configFile.absolutePath,
                            "--log-level",
                            if (DataStore.logLevel > 0) "trace" else "warn",
                        )
                    } else {
                        mutableListOf(
                            PluginManager.init("hysteria2-plugin")!!.path,
                            "client",
                            "--config",
                            configFile.absolutePath,
                            "--log-level",
                            if (DataStore.logLevel > 0) "warn" else "error",
                        )
                    }
                    if (repo.isAndroid &&
                        bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2 &&
                        bean.protocol == HysteriaBean.PROTOCOL_FAKETCP
                    ) {
                        commands.addAll(0, listOf("su", "-c"))
                    }
                    processes.start(
                        commands,
                        mutableMapOf("HYSTERIA_DISABLE_UPDATE_CHECK" to "1"),
                    )
                }

                is JuicityBean -> {
                    val configFile = File(cacheDir, "juicity_${System.currentTimeMillis()}.json")
                    configFile.writeText(cfg)
                    cacheFiles.add(configFile)
                    processes.start(
                        listOf(
                            PluginManager.init("juicity-plugin")!!.path,
                            "run",
                            "-c",
                            configFile.absolutePath,
                        ),
                        mutableMapOf("QUIC_GO_DISABLE_GSO" to "1"),
                    )
                }

                is ShadowQUICBean -> {
                    val configFile = File(cacheDir, "shadowquic_${System.currentTimeMillis()}.yaml")
                    configFile.writeText(cfg)
                    cacheFiles.add(configFile)
                    processes.start(
                        listOf(
                            PluginManager.init("shadowquic-plugin")!!.path,
                            "-c",
                            configFile.absolutePath,
                        ),
                    )
                }
            }
        }
    }
}
