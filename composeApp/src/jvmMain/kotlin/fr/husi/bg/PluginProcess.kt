package fr.husi.bg

import fr.husi.PLUGIN_CA_FILE
import fr.husi.database.DataStore
import fr.husi.database.preference.getBlocking
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.ConfigMetadata
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.hysteria.buildHysteriaConfig
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.fmt.juicity.buildJuicityConfig
import fr.husi.fmt.mieru.MieruBean
import fr.husi.fmt.mieru.buildMieruConfig
import fr.husi.fmt.naive.NaiveBean
import fr.husi.fmt.naive.buildNaiveConfig
import fr.husi.fmt.plugin.CustomPluginBean
import fr.husi.fmt.plugin.CustomPluginRenderContext
import fr.husi.fmt.plugin.buildCustomPluginConfig
import fr.husi.fmt.plugin.readPluginCaInfo
import fr.husi.fmt.plugin.renderCommandArgs
import fr.husi.fmt.plugin.renderEnvironment
import fr.husi.fmt.plugin.sanitizedConfigFileName
import fr.husi.fmt.pluginEntry
import fr.husi.fmt.protectPath
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.fmt.shadowquic.buildShadowQUICConfig
import fr.husi.ktx.invariantPathString
import fr.husi.platform.PlatformInfo
import fr.husi.plugin.PluginManager
import fr.husi.proto.v1.PluginFile
import fr.husi.proto.v1.PluginProcessSpec
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import okio.ByteString.Companion.encodeUtf8

fun resolvePluginExecutable(bean: AbstractBean): String = when (bean) {
    is CustomPluginBean -> PluginManager.resolveCustomPluginExecutable(
        pluginId = bean.pluginId,
        executablePath = bean.executablePath,
    )

    else -> PluginManager.init(bean.pluginEntry().pluginId)!!.path
}

fun initPlugins(
    metadata: ConfigMetadata,
    cacheFiles: MutableList<PlatformFile>,
): Map<Int, Pair<Int, String>> {
    val repository = resolveRepository()
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    val logLevel = DataStore.logLevel.getBlocking()
    val caInfo = readPluginCaInfo(repository.externalAssetsDir)
    val cacheDir = repository.cacheDir.invariantPathString()
    for ((chain) in metadata.externalIndex) {
        chain.entries.forEach { (port, profile) ->
            val shouldProtect = port in metadata.protectedPluginPorts
            val bean = profile.requireBean()
            resolvePluginExecutable(bean)
            when (bean) {
                is MieruBean -> {
                    pluginConfigs[port] = profile.type to bean.buildMieruConfig(port, logLevel)
                }

                is NaiveBean -> {
                    pluginConfigs[port] = profile.type to bean.buildNaiveConfig(port)
                }

                is HysteriaBean -> {
                    pluginConfigs[port] = profile.type to bean.buildHysteriaConfig(port, shouldProtect) { type ->
                        PlatformFile(repository.cacheDir, "hysteria_${System.currentTimeMillis()}.$type").also {
                            it.parent()?.createDirectories()
                            cacheFiles.add(it)
                        }
                    }
                }

                is JuicityBean -> {
                    pluginConfigs[port] = profile.type to bean.buildJuicityConfig(port, shouldProtect)
                }

                is ShadowQUICBean -> {
                    pluginConfigs[port] = profile.type to
                        bean.buildShadowQUICConfig(port, shouldProtect, logLevel) { type ->
                            PlatformFile(repository.cacheDir, "shadowquic_${System.currentTimeMillis()}.$type").also {
                                it.parent()?.createDirectories()
                                cacheFiles.add(it)
                            }
                        }
                }

                is CustomPluginBean -> {
                    pluginConfigs[port] = profile.type to bean.buildCustomPluginConfig(
                        port = port,
                        shouldProtect = shouldProtect,
                        logLevel = logLevel,
                        caPath = caInfo.path,
                        caContent = caInfo.content,
                        cacheDir = cacheDir,
                    )
                }
            }
        }
    }
    return pluginConfigs
}

fun buildPluginSpecs(
    metadata: ConfigMetadata,
    pluginConfigs: Map<Int, Pair<Int, String>>,
): List<PluginProcessSpec> {
    val repository = resolveRepository()
    val logLevel = DataStore.logLevel.getBlocking()
    val specs = ArrayList<PluginProcessSpec>()
    val caInfo = readPluginCaInfo(repository.externalAssetsDir)
    val cacheDir = repository.cacheDir.invariantPathString()

    val sharedEnv = linkedMapOf<String, String>()
    repository.externalAssetsDir.resolve(PLUGIN_CA_FILE)
        .takeIf { it.isRegularFile() }
        ?.absolutePath()
        ?.let {
            sharedEnv["SSL_CERT_FILE"] = it
            // Disable system directories.
            sharedEnv["SSL_CERT_DIR"] = "/not_exists"
        }

    for ((chain) in metadata.externalIndex) {
        chain.entries.forEach { (port, profile) ->
            val bean = profile.requireBean()
            val (_, cfg) = pluginConfigs[port] ?: return@forEach
            val executable = resolvePluginExecutable(bean)
            val env = linkedMapOf<String, String>().apply { putAll(sharedEnv) }

            val spec = when (bean) {
                is MieruBean -> {
                    val pluginId = bean.pluginEntry().pluginId
                    val configName = "mieru_$port.json"
                    if (port in metadata.protectedPluginPorts) {
                        env["MIERU_PROTECT_PATH"] = protectPath
                    }
                    env["MIERU_CONFIG_JSON_FILE"] = pluginFileToken(configName)
                    PluginProcessSpec(
                        name = pluginId,
                        command = listOf(executable, "run"),
                        environment = env,
                        files = listOf(pluginConfigFile(configName, cfg)),
                    )
                }

                is NaiveBean -> {
                    val pluginId = bean.pluginEntry().pluginId
                    val configName = "naive_$port.json"
                    PluginProcessSpec(
                        name = pluginId,
                        command = listOf(executable, pluginFileToken(configName)),
                        environment = env,
                        files = listOf(pluginConfigFile(configName, cfg)),
                    )
                }

                is HysteriaBean -> {
                    val pluginId = bean.pluginEntry().pluginId
                    val configName = "hysteria_$port.json"
                    env["HYSTERIA_DISABLE_UPDATE_CHECK"] = "1"
                    val commands = if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                        mutableListOf(
                            executable,
                            "client",
                            "--no-check",
                            "--config",
                            pluginFileToken(configName),
                            "--log-level",
                            if (logLevel > 0) "trace" else "warn",
                        )
                    } else {
                        mutableListOf(
                            executable,
                            "client",
                            "--config",
                            pluginFileToken(configName),
                            "--log-level",
                            if (logLevel > 0) "warn" else "error",
                        )
                    }
                    if (PlatformInfo.isAndroid &&
                        bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2 &&
                        bean.protocol == HysteriaBean.PROTOCOL_FAKETCP
                    ) {
                        commands.addAll(0, listOf("su", "-c"))
                    }
                    PluginProcessSpec(
                        name = pluginId,
                        command = commands,
                        environment = env,
                        files = listOf(pluginConfigFile(configName, cfg)),
                    )
                }

                is JuicityBean -> {
                    val pluginId = bean.pluginEntry().pluginId
                    val configName = "juicity_$port.json"
                    env["QUIC_GO_DISABLE_GSO"] = "1"
                    PluginProcessSpec(
                        name = pluginId,
                        command = listOf(executable, "run", "-c", pluginFileToken(configName)),
                        environment = env,
                        files = listOf(pluginConfigFile(configName, cfg)),
                    )
                }

                is ShadowQUICBean -> {
                    val pluginId = bean.pluginEntry().pluginId
                    val configName = "shadowquic_$port.yaml"
                    PluginProcessSpec(
                        name = pluginId,
                        command = listOf(executable, "-c", pluginFileToken(configName)),
                        environment = env,
                        files = listOf(pluginConfigFile(configName, cfg)),
                    )
                }

                is CustomPluginBean -> {
                    val shouldProtect = port in metadata.protectedPluginPorts
                    val configName = bean.sanitizedConfigFileName(port)
                    val hasConfig = cfg.isNotBlank()
                    val context = CustomPluginRenderContext(
                        port = port,
                        shouldProtect = shouldProtect,
                        logLevel = logLevel,
                        protectPath = if (shouldProtect) protectPath else "",
                        caPath = caInfo.path,
                        caContent = caInfo.content,
                        cacheDir = cacheDir,
                        configFileToken = if (hasConfig) pluginFileToken(configName) else "",
                    )
                    env.putAll(bean.renderEnvironment(context))
                    PluginProcessSpec(
                        name = bean.pluginId.ifBlank { bean.displayName() }.ifBlank { "custom-plugin" },
                        command = listOf(executable) + bean.renderCommandArgs(context),
                        environment = env,
                        files = if (hasConfig) {
                            listOf(pluginConfigFile(configName, cfg))
                        } else {
                            emptyList()
                        },
                    )
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

private fun pluginConfigFile(name: String, content: String) = PluginFile(name = name, content = content.encodeUtf8())
