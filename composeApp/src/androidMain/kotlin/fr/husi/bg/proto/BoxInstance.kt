package fr.husi.bg.proto

import fr.husi.bg.AbstractInstance
import fr.husi.bg.buildPluginSpecs
import fr.husi.bg.initPlugins
import fr.husi.database.ProxyEntity
import fr.husi.fmt.ConfigBuildResult
import fr.husi.fmt.buildConfig
import fr.husi.ktx.Logs
import fr.husi.ktx.deleteIfExists
import fr.husi.ktx.invariantPathString
import fr.husi.proto.v1.clientMetadata
import fr.husi.proto.v1.startServiceRequest
import fr.husi.repository.resolveAndroidRepository
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import kotlinx.coroutines.runBlocking

abstract class BoxInstance(
    val profile: ProxyEntity,
) : AbstractInstance {

    lateinit var config: ConfigBuildResult

    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    private val externalInstances = hashMapOf<Int, AbstractInstance>()
    private var cacheFiles = ArrayList<PlatformFile>()
    private var isVPN: Boolean = false

    /**
     * Config has been built. The box instance itself is only created at
     * [launch] via [startService], so call sites that only need the config
     * must not wait for [hasInstance].
     */
    fun isInitialized(): Boolean {
        return ::config.isInitialized
    }

    protected open suspend fun buildConfig() {
        config = buildConfig(profile)
    }

    open suspend fun init(isVPN: Boolean) {
        this.isVPN = isVPN
        buildConfig()
        // Fail fast on missing plugins before the foreground service escalates.
        pluginConfigs.putAll(initPlugins(config, isVPN, cacheFiles))
    }

    override fun launch() {
        for ((chain) in config.externalIndex) {
            chain.entries.forEach { (port, _) ->
                if (externalInstances.containsKey(port)) {
                    externalInstances[port]!!.launch()
                }
            }
        }
        val specs = buildPluginSpecs(config, pluginConfigs, isVPN)
        val configJson = config.config
        val request = startServiceRequest {
            this.config = configJson
            plugins.addAll(specs)
            clientMetadata = clientMetadata {
                profileId = profile.id
                profileName = profile.displayNameForService()
            }
        }
        // Match the Kotlin pool's working dir: keep plugin files out of backup.
        val pluginDir = resolveAndroidRepository().noBackupFilesDir / "plugin"
        pluginDir.createDirectories()
        resolveRepository().boxService!!.startService(
            request.toByteArray(),
            pluginDir.invariantPathString(),
        )
    }

    override fun close() {
        for (instance in externalInstances.values) {
            runCatching {
                instance.close()
            }
        }

        runBlocking {
            cacheFiles.forEach { file ->
                runCatching { file.deleteIfExists() }
            }
        }
        cacheFiles.clear()

        // Unconditional: stopService also tears down the Go plugin pool, and the
        // plugin processes must not outlive an instance that already went away.
        // A core that cannot close ends this process itself, so there is nothing
        // to recover from here.
        runCatching {
            resolveRepository().boxService?.stopService()
        }.onFailure {
            Logs.w(it)
        }
    }

}
