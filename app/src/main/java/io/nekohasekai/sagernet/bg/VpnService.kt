package io.nekohasekai.sagernet.bg

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ProxyInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import io.nekohasekai.sagernet.utils.Subnet
import android.net.VpnService as BaseVpnService

@SuppressLint("VpnServicePolicy")
class VpnService : BaseVpnService(),
    BaseService.Interface {

    companion object {

        const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
        const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"

    }

    override fun attachBaseContext(newBase: Context) {
        val languageContext = ContextCompat.getContextForLanguage(newBase)
        super.attachBaseContext(languageContext)
    }

    var conn: ParcelFileDescriptor? = null

    private var metered = false

    override var upstreamInterfaceName: String? = null

    override suspend fun startProcesses() {
        DataStore.vpnService = this
        super.startProcesses() // launch proxy instance
    }

    override var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("WakelockTimeout")
    override fun acquireWakeLock() {
        wakeLock = repo.power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sagernet:vpn")
            .apply { acquire() }
    }

    override fun killProcesses() {
        conn?.close()
        conn = null
        super.killProcesses()
    }

    override fun onBind(intent: Intent) = when (intent.action) {
        SERVICE_INTERFACE -> super<BaseVpnService>.onBind(intent)
        else -> super<BaseService.Interface>.onBind(intent)
    }

    override val data = BaseService.Data(this)
    override val tag = "SagerNetVpnService"
    override fun createNotification(profileName: String) =
        ServiceNotification(this, profileName, "service-vpn")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DataStore.serviceMode == Key.MODE_VPN) {
            if (prepare(this) != null) {
                startActivity(
                    Intent(this, VpnRequestActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else return super<BaseService.Interface>.onStartCommand(intent, flags, startId)
        }
        stopRunner()
        return START_NOT_STICKY
    }

    inner class NullConnectionException : NullPointerException(),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() = getString(R.string.reboot_required)
    }

    fun startVpn(): Int {
        // address & route & MTU ...... use GUI config
        val builder = Builder().setConfigureIntent(repo.configureIntent(this))
            .setSession(getString(R.string.app_name))
            .setMtu(DataStore.mtu)
        val networkStrategy = DataStore.networkStrategy

        when (networkStrategy) {
            SingBoxOptions.STRATEGY_IPV4_ONLY -> {
                builder.addAddress(PRIVATE_VLAN4_CLIENT, 30)
                builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
            }

            SingBoxOptions.STRATEGY_IPV6_ONLY -> {
                builder.addAddress(PRIVATE_VLAN6_CLIENT, 126)
                builder.addDnsServer(PRIVATE_VLAN6_ROUTER)
            }

            else -> {
                builder.addAddress(PRIVATE_VLAN4_CLIENT, 30)
                builder.addAddress(PRIVATE_VLAN6_CLIENT, 126)

                builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
                builder.addDnsServer(PRIVATE_VLAN6_ROUTER)
            }
        }

        // route
        if (DataStore.bypassLan) {
            resources.getStringArray(R.array.bypass_private_route).forEach {
                val subnet = Subnet.fromString(it)
                builder.addRoute(subnet.address.hostAddress!!, subnet.prefixSize)
            }
            val fakeDNSRange4 by lazy {
                DataStore.fakeDNSRange4.blankAsNull()?.let {
                    Subnet.fromString(it)
                }
            }
            val fakeDNSRange6 by lazy {
                DataStore.fakeDNSRange6.blankAsNull()?.let {
                    Subnet.fromString(it)
                }
            }
            when (networkStrategy) {
                SingBoxOptions.STRATEGY_IPV4_ONLY -> {
                    Logs.d("IPv4 Only, fake DNS: $fakeDNSRange4")
                    builder.addRoute(PRIVATE_VLAN4_ROUTER, 32)
                    fakeDNSRange4?.let {
                        builder.addRoute(it.address.hostAddress!!, it.prefixSize)
                    }
                }

                SingBoxOptions.STRATEGY_IPV6_ONLY -> {
                    Logs.d("IPv6 Only, fake DNS: $fakeDNSRange6")
                    // https://issuetracker.google.com/issues/149636790
                    builder.addRoute("2000::", 3)
                    fakeDNSRange6?.let {
                        builder.addRoute(it.address.hostAddress!!, it.prefixSize)
                    }
                }

                else -> {
                    Logs.d("Dual stack, fake DNS: $fakeDNSRange4, $fakeDNSRange6")
                    builder.addRoute(PRIVATE_VLAN4_ROUTER, 32)
                    fakeDNSRange4?.let {
                        builder.addRoute(it.address.hostAddress!!, it.prefixSize)
                    }
                    fakeDNSRange6?.let {
                        builder.addRoute(it.address.hostAddress!!, it.prefixSize)
                    }
                    builder.addRoute("2000::", 3)
                }
            }
        } else {
            when (networkStrategy) {
                SingBoxOptions.STRATEGY_IPV4_ONLY -> {
                    builder.addRoute("0.0.0.0", 0)
                }

                SingBoxOptions.STRATEGY_IPV6_ONLY -> {
                    builder.addRoute("::", 0)
                }

                else -> {
                    builder.addRoute("0.0.0.0", 0)
                    builder.addRoute("::", 0)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(metered)

        // app route
        val packageName = packageName
        val proxyApps = DataStore.proxyApps
        var bypass = DataStore.bypassMode
        val workaroundSYSTEM = false /* DataStore.tunImplementation == TunImplementation.SYSTEM */
        val needBypassRootUid = workaroundSYSTEM || data.proxy!!.config.trafficMap.values.any {
            it[0].hysteriaBean?.protocol == HysteriaBean.PROTOCOL_FAKETCP
        }

        if (proxyApps || needBypassRootUid) {
            val individual = mutableSetOf<String>()
            val allApps by lazy {
                packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).filter {
                    when (it.packageName) {
                        packageName -> false
                        "android" -> true
                        else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                    }
                }.map { it.packageName }
            }
            if (proxyApps) {
                individual.addAll(DataStore.packages.filter { it.isNotBlank() })
                if (bypass && needBypassRootUid) {
                    val individualNew = allApps.toMutableList()
                    individualNew.removeAll(individual)
                    individual.clear()
                    individual.addAll(individualNew)
                    bypass = false
                }
            } else {
                individual.addAll(allApps)
                bypass = false
            }

            val added = mutableListOf<String>()

            individual.apply {
                // Allow Matsuri itself using VPN.
                remove(packageName)
                if (!bypass) add(packageName)
            }.forEach {
                try {
                    if (bypass) {
                        builder.addDisallowedApplication(it)
                    } else {
                        builder.addAllowedApplication(it)
                    }
                    added.add(it)
                } catch (ex: PackageManager.NameNotFoundException) {
                    Logs.w(ex)
                }
            }

            if (bypass) {
                Logs.d("Add bypass: ${added.joinToString(", ")}")
            } else {
                Logs.d("Add allow: ${added.joinToString(", ")}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && DataStore.appendHttpProxy &&
            DataStore.inboundUsername.isEmpty() && DataStore.inboundPassword.isEmpty()
        ) {
            builder.setHttpProxy(
                ProxyInfo.buildDirectProxy(
                    LOCALHOST4,
                    DataStore.mixedPort,
                    DataStore.httpProxyBypass.lines().mapNotNull { line ->
                        line.trim().takeIf { it.isNotBlank() && !it.startsWith("#") }
                    },
                ).also {
                    Logs.d("Appended HTTP info: $it")
                },
            )
        }

        metered = DataStore.meteredNetwork
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(metered)

        if (DataStore.allowAppsBypassVpn) {
            builder.allowBypass()
        }

        conn = builder.establish() ?: throw NullConnectionException()

        return conn!!.fd
    }

    override fun onRevoke() = stopRunner()

    override fun onDestroy() {
        DataStore.vpnService = null
        super.onDestroy()
        data.binder.close()
    }
}
