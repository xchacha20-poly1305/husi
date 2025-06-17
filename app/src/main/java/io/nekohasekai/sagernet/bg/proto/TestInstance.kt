package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.CertProvider
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.NativeInterface
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.buildConfig
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.systemCertificates
import io.nekohasekai.sagernet.ktx.toStringIterator
import io.nekohasekai.sagernet.ktx.tryResume
import io.nekohasekai.sagernet.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import libcore.StringIterator
import kotlin.coroutines.suspendCoroutine

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    suspend fun doTest(underVPN: Boolean): Int {
        return suspendCoroutine { c ->
            processes = GuardedProcessPool {
                Logs.w(it)
                c.tryResumeWithException(it)
            }
            runOnDefaultDispatcher {
                use {
                    try {
                        init(underVPN)
                        launch()
                        if (processes.processCount > 0) {
                            // wait for plugin start
                            delay(500)
                        }

                        var enableCazilla = false
                        var certList: StringIterator? = null
                        when (DataStore.certProvider) {
                            CertProvider.SYSTEM -> {}
                            CertProvider.MOZILLA -> enableCazilla = true
                            CertProvider.SYSTEM_AND_USER -> certList = systemCertificates.let {
                                it.toStringIterator(it.size)
                            }
                        }
                        Libcore.updateRootCACerts(enableCazilla, certList)

                        c.tryResume(box.urlTest(null, link, timeout))
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                        Logs.e(e)
                    }
                }
            }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = libcore.BoxInstance(config.config, NativeInterface(true))
    }

}
