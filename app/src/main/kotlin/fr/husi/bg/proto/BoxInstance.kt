package fr.husi.bg.proto

import android.os.SystemClock
import fr.husi.bg.AbstractInstance
import fr.husi.bg.GuardedProcessPool
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.fmt.ConfigBuildResult
import fr.husi.fmt.buildConfig
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
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.libcore.Libcore
import fr.husi.plugin.PluginManager
import fr.husi.repository.repo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import java.io.File
import kotlin.system.exitProcess

abstract class BoxInstance(
    val profile: ProxyEntity,
) : AbstractInstance {

    lateinit var config: ConfigBuildResult

    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    private val externalInstances = hashMapOf<Int, AbstractInstance>()
    open lateinit var processes: GuardedProcessPool
    private var cacheFiles = ArrayList<File>()
    fun isInitialized(): Boolean {
        return ::config.isInitialized && repo.boxService?.hasInstance() == true
    }

    protected open fun buildConfig() {
        config = buildConfig(profile)
    }

    protected open suspend fun loadConfig() {
        repo.boxService!!.newInstance(config.config)
    }

    companion object {
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
                            pluginConfigs[port] =
                                profile.type to bean.buildMieruConfig(port, logLevel)
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
                                    File(
                                        repo.cacheDir,
                                        "hysteria_" + SystemClock.elapsedRealtime() + ".$type",
                                    ).apply {
                                        parentFile?.mkdirs()
                                        cacheFiles.add(this)
                                    }
                                }
                        }

                        is JuicityBean -> {
                            PluginManager.init("juicity-plugin")
                            pluginConfigs[port] =
                                profile.type to bean.buildJuicityConfig(port, isVPN)
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
                            val configFile =
                                File(cacheDir, "mieru_${SystemClock.elapsedRealtime()}.json")
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
                            val configFile =
                                File(cacheDir, "naive_${SystemClock.elapsedRealtime()}.json")
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
                            val configFile =
                                File(cacheDir, "hysteria_${SystemClock.elapsedRealtime()}.json")
                            configFile.writeText(cfg)
                            cacheFiles.add(configFile)
                            val commands =
                                if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                                    mutableListOf(
                                        PluginManager.init("hysteria-plugin")!!.path, "client",
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
                            if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
                                && bean.protocol == HysteriaBean.PROTOCOL_FAKETCP
                            ) {
                                commands.addAll(0, listOf("su", "-c"))
                            }
                            processes.start(
                                commands,
                                mutableMapOf("HYSTERIA_DISABLE_UPDATE_CHECK" to "1"),
                            )
                        }

                        is JuicityBean -> {
                            val configFile =
                                File(cacheDir, "juicity_" + SystemClock.elapsedRealtime() + ".json")
                            configFile.writeText(cfg)
                            cacheFiles.add(configFile)
                            processes.start(
                                listOf(
                                    PluginManager.init("juicity-plugin")!!.path, "run",
                                    "-c",
                                    configFile.absolutePath,
                                ),
                                mutableMapOf("QUIC_GO_DISABLE_GSO" to "1"),
                            )
                        }

                        is ShadowQUICBean -> {
                            val configFile =
                                File(cacheDir, "shadowquic_${SystemClock.elapsedRealtime()}.yaml")
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
    }

    open suspend fun init(isVPN: Boolean) {
        buildConfig()
        pluginConfigs.putAll(initPlugins(config, isVPN, cacheFiles))
        loadConfig()
    }

    override fun launch() {
        for ((chain) in config.externalIndex) {
            chain.entries.forEach { (port, _) ->
                if (externalInstances.containsKey(port)) {
                    externalInstances[port]!!.launch()
                }
            }
        }
        launchPlugins(config, pluginConfigs, processes, cacheFiles)
        repo.boxService!!.startInstance()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun close() {
        for (instance in externalInstances.values) {
            runCatching {
                instance.close()
            }
        }

        cacheFiles.removeAll { it.delete(); true }

        if (::processes.isInitialized) processes.close(GlobalScope + Dispatchers.IO)

        if (repo.boxService?.hasInstance() == true) {
            try {
                repo.boxService!!.stopInstance()
            } catch (e: Exception) {
                Logs.w(e)
                // Kill the process if it is not closed properly to clean exist inbound listeners.
                // Do not kill in main process, whose test not starts any listener.
                if (!repo.isMainProcess && e.readableMessage.contains("sing-box did not close in time")) runOnDefaultDispatcher {
                    delay(500) // Wait for error handling
                    exitProcess(0)
                }
            }
        }
    }

}
