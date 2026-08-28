package fr.husi.database

import fr.husi.CONNECTION_TEST_URL
import fr.husi.CertProvider
import fr.husi.DEFAULT_HTTP_BYPASS
import fr.husi.DOMAIN_STRATEGY_AUTO
import fr.husi.GroupType
import fr.husi.Key
import fr.husi.NetworkInterfaceStrategy
import fr.husi.ProtocolProvider
import fr.husi.SPEED_TEST_UPLOAD_URL
import fr.husi.SPEED_TEST_URL
import fr.husi.TrafficSortMode
import fr.husi.TunImplementation
import fr.husi.bg.ServiceState
import fr.husi.compose.theme.DEFAULT
import fr.husi.database.preference.DataStorePreferenceDataStore
import fr.husi.database.preference.boolean
import fr.husi.database.preference.createConfigurationDataStore
import fr.husi.database.preference.int
import fr.husi.database.preference.long
import fr.husi.database.preference.port
import fr.husi.database.preference.preferenceStoreScope
import fr.husi.database.preference.string
import fr.husi.database.preference.stringSet
import fr.husi.platform.PlatformInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

object DataStore {

    // Per-process copy of the current service state. @Volatile does not cross
    // processes: the UI process is fed by ServiceEventMirror, :bg by BaseService,
    // and desktop by CoreHostController.
    @Volatile
    var serviceState = ServiceState.Idle

    val configurationStore = DataStorePreferenceDataStore.create(
        createConfigurationDataStore(preferenceStoreScope()),
    )

    init {
        // Migration
        val keyIndividual = "individual"
        val oldPackages = configurationStore.getString(keyIndividual)?.split("\n")
        if (oldPackages?.isNotEmpty() == true && configurationStore.getStringSet(Key.PACKAGES) == null) {
            configurationStore.putStringSet(Key.PACKAGES, oldPackages.toMutableSet())
            // remove old key
            configurationStore.remove(keyIndividual)
        }

        val keyTCPKeepAliveInterval = "tcpKeepAliveInterval"
        configurationStore.getString(keyTCPKeepAliveInterval)?.let { oldTCPKeepAlive ->
            if (oldTCPKeepAlive.lastOrNull()?.isLetter() == true) {
                configurationStore.putString(Key.TCP_KEEP_ALIVE_INTERVAL_0, oldTCPKeepAlive)
            } else {
                val seconds = oldTCPKeepAlive.toIntOrNull() ?: 75
                configurationStore.putString(Key.TCP_KEEP_ALIVE_INTERVAL_0, "${seconds}s")
            }
            configurationStore.remove(keyTCPKeepAliveInterval)
        }
    }

    // last used, but may not be running
    val currentProfile = configurationStore.long(Key.PROFILE_CURRENT)

    val selectedProxy = configurationStore.long(Key.PROFILE_ID)

    /** No group use this ID */
    const val GROUP_NOPE = -1

    /**
     * The stored value is [GROUP_NOPE] until a group is picked. Resolving that to a
     * real group can create the default group and is a database round trip, so it
     * lives in [currentGroupId] / [currentGroup].
     */
    val selectedGroup = configurationStore.long(Key.PROFILE_GROUP)

    suspend fun currentGroupId(): Long {
        val currentSelected = selectedGroup.getOrNull()
        if (currentSelected != null && currentSelected > GROUP_NOPE) return currentSelected
        val groupId = ProfileManager.ensureDefaultGroupId()
        selectedGroup.set(groupId)
        return groupId
    }

    suspend fun currentGroup(): ProxyGroup {
        val currentSelected = selectedGroup.getOrNull()
        if (currentSelected != null && currentSelected > GROUP_NOPE) {
            val group = SagerDatabase.groupDao.getById(currentSelected).firstOrNull()
            if (group != null) return group
        }
        val groupId = ProfileManager.ensureDefaultGroupId()
        val group = SagerDatabase.groupDao.getById(groupId).firstOrNull()
            ?: SagerDatabase.groupDao.allGroups().first().first()
        selectedGroup.set(group.id)
        return group
    }

    suspend fun selectedGroupForImport(): Long {
        val current = currentGroup()
        if (current.type == GroupType.BASIC) return current.id
        val groups = SagerDatabase.groupDao.allGroups().first()
        return groups.find { it.type == GroupType.BASIC }!!.id
    }

    val isExpert = configurationStore.boolean(Key.APP_EXPERT)
    val appTheme = configurationStore.int(Key.APP_THEME) { DEFAULT }
    val nightTheme = configurationStore.int(Key.NIGHT_THEME)
    val appLanguage = configurationStore.string(Key.APP_LANGUAGE)
    val serviceMode = configurationStore.string(Key.SERVICE_MODE) { Key.MODE_VPN }
    val debugListen = configurationStore.string(Key.DEBUG_LISTEN)
    val networkStrategy = configurationStore.string(Key.NETWORK_STRATEGY)
    val anchorSSID = configurationStore.string(Key.ANCHOR_SSID)

    val networkInterfaceType = configurationStore.int(Key.NETWORK_INTERFACE_STRATEGY) {
        NetworkInterfaceStrategy.DEFAULT
    }
    val networkPreferredInterfaces = configurationStore.stringSet(Key.NETWORK_PREFERRED_INTERFACES)
    val forcedSearchProcess = configurationStore.boolean(Key.FORCED_SEARCH_PROCESS) { false }

    val disableTcpKeepAlive = configurationStore.boolean(Key.DISABLE_TCP_KEEP_ALIVE) { PlatformInfo.isAndroid }
    val tcpKeepAliveIdle = configurationStore.string(Key.TCP_KEEP_ALIVE_IDLE) { "5m" }
    val tcpKeepAliveInterval = configurationStore.string(Key.TCP_KEEP_ALIVE_INTERVAL_0) { "75s" }
    val mtu = configurationStore.int(Key.MTU) { 9000 }
    val vpnSessionName = configurationStore.string(Key.VPN_SESSION_NAME) { "" }
    val tunInterfaceName = configurationStore.string(Key.TUN_INTERFACE_NAME) { "" }
    val tunStrictRoute = configurationStore.boolean(Key.TUN_STRICT_ROUTE) { true }
    val tunAutoRedirect = configurationStore.boolean(Key.TUN_AUTO_REDIRECT) { true }
    val allowAppsBypassVpn = configurationStore.boolean(Key.ALLOW_APPS_BYPASS_VPN) { false }

    val bypassLan = configurationStore.boolean(Key.BYPASS_LAN) { true }
    val inboundUsername = configurationStore.string(Key.INBOUND_USERNAME) { "" }
    val inboundPassword = configurationStore.string(Key.INBOUND_PASSWORD) { "" }

    val allowAccess = configurationStore.boolean(Key.ALLOW_ACCESS)
    val speedInterval = configurationStore.int(Key.SPEED_INTERVAL) { 1000 }
    val showGroupInNotification = configurationStore.boolean(Key.SHOW_GROUP_IN_NOTIFICATION)

    val remoteDns = configurationStore.string(Key.REMOTE_DNS) { "tcp://dns.google" }
    val directDns = configurationStore.string(Key.DIRECT_DNS) { "local" }
    val mDNS = configurationStore.string(Key.MDNS) { "" }
    // Consumers strip "auto" back to an empty strategy, so it is the neutral default.
    val domainStrategyForDirect = configurationStore.string(Key.DOMAIN_STRATEGY_FOR_DIRECT) {
        DOMAIN_STRATEGY_AUTO
    }
    val domainStrategyForServer = configurationStore.string(Key.DOMAIN_STRATEGY_FOR_SERVER) {
        DOMAIN_STRATEGY_AUTO
    }
    val enableFakeDns = configurationStore.boolean(Key.ENABLE_FAKE_DNS) { false }
    val fakeDNSForAll = configurationStore.boolean(Key.FAKE_DNS_FOR_ALL) { false }

    // https://developer.chrome.com/blog/local-network-access
    // Use the address belongs to these "local" networks
    // (https://wicg.github.io/local-network-access/#non-public-ip-address-blocks)
    // will make permission warning in Chrome.
    // To avoid user agreeing plenty of permissions, we decide to use these new address.
    // The pre-defined IPv4 range is limited, change to whatever user like.
    val fakeDNSRange4 = configurationStore.string(Key.FAKE_DNS_RANGE_4) { "198.51.100.0/24" }
    val fakeDNSRange6 = configurationStore.string(Key.FAKE_DNS_RANGE_6) { "2001:2::/48" }
    val dnsHosts = configurationStore.string(Key.DNS_HOSTS)
    val dnsOptimisticCache = configurationStore.string(Key.DNS_OPTIMISTIC_CACHE) { "" }

    val securityAdvisory = configurationStore.boolean(Key.SECURITY_ADVISORY) { true }
    val rulesProvider = configurationStore.int(Key.RULES_PROVIDER)
    val customRuleProvider = configurationStore.string(Key.CUSTOM_RULE_PROVIDER)
    val routeAssetsAutoUpdateDelay = configurationStore.int(Key.ROUTE_ASSETS_AUTO_UPDATE_DELAY) { 0 }
    val routeAssetsLastUpdated = configurationStore.long(Key.ROUTE_ASSETS_LAST_UPDATED) { 0L }
    val logLevel = configurationStore.int(Key.LOG_LEVEL) { 3 /* WARN */ }
    val logMaxLine = configurationStore.int(Key.LOG_MAX_LINE) { 1024 }
    val acquireWakeLock = configurationStore.boolean(Key.ACQUIRE_WAKE_LOCK)

    val mixedPort = configurationStore.port(Key.MIXED_PORT, 2080)
    val localDNSPort = configurationStore.port(Key.LOCAL_DNS_PORT, 0)

    suspend fun initGlobal() {
        if (mixedPort.getOrNull() == null) {
            mixedPort.set(mixedPort.get())
        }
        if (localDNSPort.getOrNull() == null) {
            localDNSPort.set(localDNSPort.get())
        }
    }

    val meteredNetwork = configurationStore.boolean(Key.METERED_NETWORK)
    val proxyApps = configurationStore.boolean(Key.PROXY_APPS)
    val updateProxyAppsWhenInstall = configurationStore.boolean(Key.UPDATE_PROXY_APPS_WHEN_INSTALL)
    val bypassMode = configurationStore.boolean(Key.BYPASS_MODE) { true } // VPN bypass mode

    val packages = configurationStore.stringSet(Key.PACKAGES)
    val showDirectSpeed = configurationStore.boolean(Key.SHOW_DIRECT_SPEED) { true }

    val persistAcrossReboot = configurationStore.boolean(Key.PERSIST_ACROSS_REBOOT) { false }

    val appendHttpProxy = configurationStore.boolean(Key.APPEND_HTTP_PROXY)
    val httpProxyBypass = configurationStore.string(Key.HTTP_PROXY_BYPASS) { DEFAULT_HTTP_BYPASS }

    val connectionTestURL = configurationStore.string(Key.CONNECTION_TEST_URL) { CONNECTION_TEST_URL }
    val connectionTestConcurrent = configurationStore.int(Key.CONNECTION_TEST_CONCURRENT) { 5 }
    val connectionTestTimeout = configurationStore.int(Key.CONNECTION_TEST_TIMEOUT) { 3000 }
    val connectionTestUnifiedDelay = configurationStore.boolean(Key.CONNECTION_TEST_UNIFIED_DELAY) { false }
    val connectionTestIgnoreHandshakeTime = configurationStore.boolean(Key.CONNECTION_TEST_IGNORE_HANDSHAKE_TIME) { false }

    val alwaysShowAddress = configurationStore.boolean(Key.ALWAYS_SHOW_ADDRESS)
    val blurredAddress = configurationStore.boolean(Key.BLURRED_ADDRESS)
    val privacyMode = configurationStore.boolean(Key.PRIVACY_MODE) { false }

    val providerHysteria2 = configurationStore.int(Key.PROVIDER_HYSTERIA2) { ProtocolProvider.CORE }
    val providerJuicity = configurationStore.int(Key.PROVIDER_JUICITY) { ProtocolProvider.PLUGIN }
    val providerNaive = configurationStore.int(Key.PROVIDER_NAIVE) { ProtocolProvider.CORE }

    val tunImplementation = configurationStore.int(Key.TUN_IMPLEMENTATION) { TunImplementation.MIXED }
    val profileTrafficStatistics = configurationStore.boolean(Key.PROFILE_TRAFFIC_STATISTICS) { true }
    val certProvider = configurationStore.int(Key.CERT_PROVIDER) { CertProvider.MOZILLA }
    val disableProcessText = configurationStore.boolean(Key.DISABLE_PROCESS_TEXT)
    val hideLauncherIcon = configurationStore.boolean(Key.HIDE_LAUNCHER_ICON)

    val trafficDescending = configurationStore.boolean(Key.TRAFFIC_DESCENDING) { false }
    val trafficSortMode = configurationStore.int(Key.TRAFFIC_SORT_MODE) { TrafficSortMode.START }
    val trafficConnectionQuery = configurationStore.int(Key.TRAFFIC_CONNECTION_QUERY) { 1 shl 0 }
    val proxySetOrder = configurationStore.int(Key.PROXY_SET_ORDER)

    val speedTestUrl = configurationStore.string(Key.SPEED_TEST_URL) { SPEED_TEST_URL }
    val speedTestUploadURL = configurationStore.string(Key.SPEED_TEST_UPLOAD_URL) { SPEED_TEST_UPLOAD_URL }
    val speedTestUploadLength = configurationStore.long(Key.SPEED_TEST_UPLOAD_LENGTH) { 10 * 1024 * 1024 }
    val speedTestTimeout = configurationStore.int(Key.SPEED_TEST_TIMEOUT) { 20000 }

    // ntp
    val ntpEnable = configurationStore.boolean(Key.ENABLE_NTP) { false }
    val ntpAddress = configurationStore.string(Key.NTP_SERVER) { "time.apple.com" }
    val ntpPort = configurationStore.int(Key.NTP_PORT) { 123 }
    val ntpInterval = configurationStore.string(Key.NTP_INTERVAL) { "30m" }

    // protocol

    val uploadSpeed = configurationStore.int(Key.UPLOAD_SPEED) { 0 }
    val downloadSpeed = configurationStore.int(Key.DOWNLOAD_SPEED) { 0 }
    val customPluginPrefix = configurationStore.string(Key.CUSTOM_PLUGIN_PREFIX)

    val rulesFirstCreate = configurationStore.boolean(Key.RULES_FIRST_CREATE)

    val desktopNavRailWidth = configurationStore.int(Key.DESKTOP_NAV_RAIL_WIDTH) { 220 }

    val activeRemoteServerId = configurationStore.long(Key.ACTIVE_REMOTE_SERVER_ID)

}
