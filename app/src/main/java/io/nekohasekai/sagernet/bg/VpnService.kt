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
            for (cidr in privateRoutes) {
                val address = cidr.substringBefore("/")
                val prefixLength = cidr.substringAfter("/").toInt()
                builder.addRoute(address, prefixLength)
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
                    builder.addRoute(PRIVATE_VLAN4_ROUTER, 32)
                    fakeDNSRange4?.let {
                        builder.addRoute(it.address.hostAddress!!, it.prefixSize)
                    }
                }

                SingBoxOptions.STRATEGY_IPV6_ONLY -> {
                    // https://issuetracker.google.com/issues/149636790
                    builder.addRoute("2000::", 3)
                    fakeDNSRange6?.let {
                        builder.addRoute(it.address.hostAddress!!, it.prefixSize)
                    }
                }

                else -> {
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

    private val privateRoutes
        get() = listOf(
            "1.0.0.0/8",
            "2.0.0.0/7",
            "4.0.0.0/6",
            "8.0.0.0/7",
            "11.0.0.0/8",
            "12.0.0.0/6",
            "16.0.0.0/4",
            "32.0.0.0/3",
            "64.0.0.0/3",
            "96.0.0.0/6",
            "100.0.0.0/10",
            "100.128.0.0/9",
            "101.0.0.0/8",
            "102.0.0.0/7",
            "104.0.0.0/5",
            "112.0.0.0/5",
            "120.0.0.0/6",
            "124.0.0.0/7",
            "126.0.0.0/8",
            "128.0.0.0/3",
            "160.0.0.0/5",
            "168.0.0.0/8",
            "169.0.0.0/9",
            "169.128.0.0/10",
            "169.192.0.0/11",
            "169.224.0.0/12",
            "169.240.0.0/13",
            "169.248.0.0/14",
            "169.252.0.0/15",
            "169.255.0.0/16",
            "170.0.0.0/7",
            "172.0.0.0/12",
            "172.32.0.0/11",
            "172.64.0.0/10",
            "172.128.0.0/9",
            "173.0.0.0/8",
            "174.0.0.0/7",
            "176.0.0.0/4",
            "192.0.1.0/24",
            "192.0.3.0/24",
            "192.0.4.0/22",
            "192.0.8.0/21",
            "192.0.16.0/20",
            "192.0.32.0/19",
            "192.0.64.0/18",
            "192.0.128.0/17",
            "192.1.0.0/16",
            "192.2.0.0/15",
            "192.4.0.0/14",
            "192.8.0.0/13",
            "192.16.0.0/12",
            "192.32.0.0/11",
            "192.64.0.0/12",
            "192.80.0.0/13",
            "192.88.0.0/18",
            "192.88.64.0/19",
            "192.88.96.0/23",
            "192.88.98.0/24",
            "192.88.100.0/22",
            "192.88.104.0/21",
            "192.88.112.0/20",
            "192.88.128.0/17",
            "192.89.0.0/16",
            "192.90.0.0/15",
            "192.92.0.0/14",
            "192.96.0.0/11",
            "192.128.0.0/11",
            "192.160.0.0/13",
            "192.169.0.0/16",
            "192.170.0.0/15",
            "192.172.0.0/14",
            "192.176.0.0/12",
            "192.192.0.0/10",
            "193.0.0.0/8",
            "194.0.0.0/7",
            "196.0.0.0/7",
            "198.0.0.0/12",
            "198.16.0.0/15",
            "198.20.0.0/14",
            "198.24.0.0/13",
            "198.32.0.0/12",
            "198.48.0.0/15",
            "198.50.0.0/16",
            "198.51.0.0/18",
            "198.51.64.0/19",
            "198.51.96.0/22",
            "198.51.101.0/24",
            "198.51.102.0/23",
            "198.51.104.0/21",
            "198.51.112.0/20",
            "198.51.128.0/17",
            "198.52.0.0/14",
            "198.56.0.0/13",
            "198.64.0.0/10",
            "198.128.0.0/9",
            "199.0.0.0/8",
            "200.0.0.0/7",
            "202.0.0.0/8",
            "203.0.0.0/18",
            "203.0.64.0/19",
            "203.0.96.0/20",
            "203.0.112.0/24",
            "203.0.114.0/23",
            "203.0.116.0/22",
            "203.0.120.0/21",
            "203.0.128.0/17",
            "203.1.0.0/16",
            "203.2.0.0/15",
            "203.4.0.0/14",
            "203.8.0.0/13",
            "203.16.0.0/12",
            "203.32.0.0/11",
            "203.64.0.0/10",
            "203.128.0.0/9",
            "204.0.0.0/6",
            "208.0.0.0/4",
        )
}
