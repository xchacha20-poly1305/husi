package io.nekohasekai.sagernet.bg.proto

import android.os.SystemClock
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.AbstractInstance
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.NativeInterface
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.ConfigBuildResult
import io.nekohasekai.sagernet.fmt.buildConfig
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildHysteriaConfig
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.juicity.buildJuicityConfig
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.mieru.buildMieruConfig
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.naive.buildNaiveConfig
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.plugin.PluginManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.plus
import libcore.BoxInstance
import libcore.Libcore
import java.io.File

abstract class BoxInstance(
    val profile: ProxyEntity,
) : AbstractInstance {

    lateinit var config: ConfigBuildResult
    lateinit var box: BoxInstance

    private val pluginPath = hashMapOf<String, PluginManager.InitResult>()
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    private val externalInstances = hashMapOf<Int, AbstractInstance>()
    open lateinit var processes: GuardedProcessPool
    private var cacheFiles = ArrayList<File>()
    fun isInitialized(): Boolean {
        return ::config.isInitialized && ::box.isInitialized
    }

    protected fun initPlugin(name: String): PluginManager.InitResult {
        return pluginPath.getOrPut(name) { PluginManager.init(name)!! }
    }

    protected open fun buildConfig() {
        config = buildConfig(profile)
    }

    protected open suspend fun loadConfig() {
        box = BoxInstance(config.config, NativeInterface())
    }

    open suspend fun init() {
        buildConfig()
        for ((chain) in config.externalIndex) {
            chain.entries.forEach { (port, profile) ->
                when (val bean = profile.requireBean()) {

                    is MieruBean -> {
                        initPlugin("mieru-plugin")
                        pluginConfigs[port] =
                            profile.type to bean.buildMieruConfig(port, DataStore.logLevel)
                    }

                    is NaiveBean -> {
                        initPlugin("naive-plugin")
                        pluginConfigs[port] = profile.type to bean.buildNaiveConfig(port)
                    }

                    is HysteriaBean -> {
                        when (bean.protocolVersion) {
                            HysteriaBean.PROTOCOL_VERSION_1 -> initPlugin("hysteria-plugin")
                            HysteriaBean.PROTOCOL_VERSION_2 -> initPlugin("hysteria2-plugin")
                        }
                        pluginConfigs[port] = profile.type to bean.buildHysteriaConfig(port) {
                            File(
                                app.cacheDir, "hysteria_" + SystemClock.elapsedRealtime() + ".ca"
                            ).apply {
                                parentFile?.mkdirs()
                                cacheFiles.add(this)
                            }
                        }
                    }

                    is JuicityBean -> {
                        initPlugin("juicity-plugin")
                        pluginConfigs[port] = profile.type to bean.buildJuicityConfig(port)
                    }
                }
            }
        }
        loadConfig()
    }

    override fun launch() {
        // TODO move, this is not box
        val cacheDir = File(SagerNet.application.cacheDir, "tmpcfg")
        cacheDir.mkdirs()

        for ((chain) in config.externalIndex) {
            chain.entries.forEach { (port, profile) ->
                val bean = profile.requireBean()
                val (_, config) = pluginConfigs[port] ?: (0 to "")

                when {
                    externalInstances.containsKey(port) -> {
                        externalInstances[port]!!.launch()
                    }

                    bean is MieruBean -> {
                        val configFile = File(
                            cacheDir, "mieru_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val envMap = mutableMapOf(
                            "MIERU_CONFIG_JSON_FILE" to configFile.absolutePath,
                            "MIERU_PROTECT_PATH" to "protect_path",
                        )

                        val commands = mutableListOf(
                            initPlugin("mieru-plugin").path, "run",
                        )

                        processes.start(commands, envMap)
                    }

                    bean is NaiveBean -> {
                        val configFile = File(
                            cacheDir, "naive_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val envMap = mutableMapOf<String, String>()

                        val commands = mutableListOf(
                            initPlugin("naive-plugin").path, configFile.absolutePath
                        )

                        processes.start(commands, envMap)
                    }

                    bean is HysteriaBean -> {
                        val configFile = File(
                            cacheDir, "hysteria_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val envMap = mutableMapOf("HYSTERIA_DISABLE_UPDATE_CHECK" to "1")

                        val commands =
                            if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                                mutableListOf(
                                    initPlugin("hysteria-plugin").path,
                                    "--no-check",
                                    "--config",
                                    configFile.absolutePath,
                                    "--log-level",
                                    if (DataStore.logLevel > 0) "trace" else "warn",
                                    "client",
                                )
                            } else {
                                mutableListOf(
                                    initPlugin("hysteria2-plugin").path, "client",
                                    "--config", configFile.absolutePath,
                                    "--log-level", if (DataStore.logLevel > 0) "warn" else "error",
                                )
                            }

                        if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
                            && bean.protocol == HysteriaBean.PROTOCOL_FAKETCP
                        ) {
                            commands.addAll(0, listOf("su", "-c"))
                        }

                        processes.start(commands, envMap)
                    }

                    bean is JuicityBean -> {
                        val configFile = File(
                            cacheDir, "juicity_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val envMap = mutableMapOf(
                            "QUIC_GO_DISABLE_GSO" to "1"
                        )

                        val commands = mutableListOf(
                            initPlugin("juicity-plugin").path,
                            "run",
                            "-c", configFile.absolutePath,
                        )

                        processes.start(commands, envMap)
                    }

                }
            }
        }

        box.start()
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

        if (::box.isInitialized) {
            try {
                box.close()
            } catch (e: Exception) {
                Logs.w(e)
                Libcore.kill()
            }
        }
    }

}
