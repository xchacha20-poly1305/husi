package io.nekohasekai.sagernet.database

import android.os.Binder
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.CONNECTION_TEST_URL
import io.nekohasekai.sagernet.CertProvider
import io.nekohasekai.sagernet.DEFAULT_HTTP_BYPASS
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.MuxStrategy
import io.nekohasekai.sagernet.MuxType
import io.nekohasekai.sagernet.NetworkInterfaceStrategy
import io.nekohasekai.sagernet.SPEED_TEST_URL
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.ktx.boolean
import io.nekohasekai.sagernet.ktx.int
import io.nekohasekai.sagernet.ktx.long
import io.nekohasekai.sagernet.ktx.parsePort
import io.nekohasekai.sagernet.ktx.string
import io.nekohasekai.sagernet.ktx.stringSet
import io.nekohasekai.sagernet.ktx.stringToInt
import io.nekohasekai.sagernet.ktx.stringToIntIfExists
import io.nekohasekai.sagernet.ktx.stringToLong

object DataStore : OnPreferenceDataStoreChangeListener {

    // share service state in main & bg process
    @Volatile
    var serviceState = BaseService.State.Idle

    val configurationStore = RoomPreferenceDataStore(PublicDatabase.kvPairDao)
    val profileCacheStore = RoomPreferenceDataStore(TempDatabase.profileCacheDao)

    init {
        // Migration
        val oldPackages = configurationStore.getString("individual")?.split("\n")
        if (oldPackages?.isNotEmpty() == true && configurationStore.getStringSet(Key.PACKAGES) == null) {
            configurationStore.putStringSet(Key.PACKAGES, oldPackages.toMutableSet())
            // remove old key
            configurationStore.remove("individual")
        }
    }

    // last used, but may not be running
    var currentProfile by configurationStore.long(Key.PROFILE_CURRENT)

    var selectedProxy by configurationStore.long(Key.PROFILE_ID)
    var selectedGroup by configurationStore.long(Key.PROFILE_GROUP) { currentGroupId() } // "ungrouped" group id = 1

    // only in bg process
    var vpnService: VpnService? = null
    var baseService: BaseService.Interface? = null

    fun currentGroupId(): Long {
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) return currentSelected
        val groups = SagerDatabase.groupDao.allGroups()
        if (groups.isNotEmpty()) {
            val groupId = groups[0].id
            selectedGroup = groupId
            return groupId
        }
        val groupId = SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
        selectedGroup = groupId
        return groupId
    }

    fun currentGroup(): ProxyGroup {
        var group: ProxyGroup? = null
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) {
            group = SagerDatabase.groupDao.getById(currentSelected)
        }
        if (group != null) return group
        val groups = SagerDatabase.groupDao.allGroups()
        if (groups.isEmpty()) {
            group = ProxyGroup(ungrouped = true).apply {
                id = SagerDatabase.groupDao.createGroup(this)
            }
        } else {
            group = groups[0]
        }
        selectedGroup = group.id
        return group
    }

    fun selectedGroupForImport(): Long {
        val current = currentGroup()
        if (current.type == GroupType.BASIC) return current.id
        val groups = SagerDatabase.groupDao.allGroups()
        return groups.find { it.type == GroupType.BASIC }!!.id
    }

    var isExpert by configurationStore.boolean(Key.APP_EXPERT)
    var appTheme by configurationStore.int(Key.APP_THEME)
    var nightTheme by configurationStore.stringToInt(Key.NIGHT_THEME)
    var serviceMode by configurationStore.string(Key.SERVICE_MODE) { Key.MODE_VPN }
    var memoryLimit by configurationStore.boolean(Key.MEMORY_LIMIT) { false }
    var debugListen by configurationStore.string(Key.DEBUG_LISTEN)
    var networkStrategy by configurationStore.string(Key.NETWORK_STRATEGY)
    var anchorSSID by configurationStore.string(Key.ANCHOR_SSID)

    var networkInterfaceType by configurationStore.stringToInt(Key.NETWORK_INTERFACE_STRATEGY) {
        NetworkInterfaceStrategy.DEFAULT
    }
    var networkPreferredInterfaces by configurationStore.stringSet(Key.NETWORK_PREFERRED_INTERFACES)
    var forcedSearchProcess by configurationStore.boolean(Key.FORCED_SEARCH_PROCESS) { false }

    //    var tcpKeepAliveInterval by configurationStore.stringToInt(Key.TCP_KEEP_ALIVE_INTERVAL) { 15 }
    var mtu by configurationStore.stringToInt(Key.MTU) { 9000 }
    var allowAppsBypassVpn by configurationStore.boolean(Key.ALLOW_APPS_BYPASS_VPN) { false }

    var bypassLan by configurationStore.boolean(Key.BYPASS_LAN)
    var bypassLanInCore by configurationStore.boolean(Key.BYPASS_LAN_IN_CORE)
    var inboundUsername by configurationStore.string(Key.INBOUND_USERNAME) { "" }
    var inboundPassword by configurationStore.string(Key.INBOUND_PASSWORD) { "" }

    var allowAccess by configurationStore.boolean(Key.ALLOW_ACCESS)
    var speedInterval by configurationStore.stringToInt(Key.SPEED_INTERVAL)
    var showGroupInNotification by configurationStore.boolean(Key.SHOW_GROUP_IN_NOTIFICATION)

    var remoteDns by configurationStore.string(Key.REMOTE_DNS) { "tcp://dns.google" }
    var directDns by configurationStore.string(Key.DIRECT_DNS) { "local" }
    var domainStrategyForDirect by configurationStore.string(Key.DOMAIN_STRATEGY_FOR_DIRECT)
    var domainStrategyForServer by configurationStore.string(Key.DOMAIN_STRATEGY_FOR_SERVER)
    var enableFakeDns by configurationStore.boolean(Key.ENABLE_FAKE_DNS) { false }
    var dnsHosts by configurationStore.string(Key.DNS_HOSTS)

    var securityAdvisory by configurationStore.boolean(Key.SECURITY_ADVISORY) { true }
    var rulesProvider by configurationStore.stringToInt(Key.RULES_PROVIDER)
    var customRuleProvider by configurationStore.string(Key.CUSTOM_RULE_PROVIDER)
    var logLevel by configurationStore.stringToInt(Key.LOG_LEVEL)
    var logMaxSize by configurationStore.stringToInt(Key.LOG_MAX_SIZE) { 50 }
    var acquireWakeLock by configurationStore.boolean(Key.ACQUIRE_WAKE_LOCK)

    // hopefully hashCode = mHandle doesn't change, currently this is true from KitKat to Nougat
    private val userIndex by lazy { Binder.getCallingUserHandle().hashCode() }
    var mixedPort: Int
        get() = getLocalPort(Key.MIXED_PORT, 2080)
        set(value) = saveLocalPort(Key.MIXED_PORT, value)
    var localDNSPort: Int
        get() = getLocalPort(Key.LOCAL_DNS_PORT, 0)
        set(value) {
            saveLocalPort(Key.LOCAL_DNS_PORT, value)
        }

    fun initGlobal() {
        if (configurationStore.getString(Key.MIXED_PORT) == null) {
            mixedPort = mixedPort
        }
        if (configurationStore.getString(Key.LOCAL_DNS_PORT) == null) {
            localDNSPort = localDNSPort
        }
    }


    private fun getLocalPort(key: String, default: Int): Int {
        return parsePort(configurationStore.getString(key), default + userIndex)
    }

    private fun saveLocalPort(key: String, value: Int) {
        configurationStore.putString(key, "$value")
    }

    var meteredNetwork by configurationStore.boolean(Key.METERED_NETWORK)
    var proxyApps by configurationStore.boolean(Key.PROXY_APPS)
    var updateProxyAppsWhenInstall by configurationStore.boolean(Key.UPDATE_PROXY_APPS_WHEN_INSTALL)
    var bypassMode by configurationStore.boolean(Key.BYPASS_MODE) { true } // VPN bypass mode

    // var individual by configurationStore.string(Key.INDIVIDUAL) // old packages that split by '\n'
    var packages by configurationStore.stringSet(Key.PACKAGES)
    var showDirectSpeed by configurationStore.boolean(Key.SHOW_DIRECT_SPEED) { true }

    val persistAcrossReboot by configurationStore.boolean(Key.PERSIST_ACROSS_REBOOT) { false }

    var appendHttpProxy by configurationStore.boolean(Key.APPEND_HTTP_PROXY)
    var httpProxyBypass by configurationStore.string(Key.HTTP_PROXY_BYPASS) { DEFAULT_HTTP_BYPASS }
    var connectionTestURL by configurationStore.string(Key.CONNECTION_TEST_URL) { CONNECTION_TEST_URL }
    var connectionTestConcurrent by configurationStore.int(Key.CONNECTION_TEST_CONCURRENT) { 5 }
    var connectionTestTimeout by configurationStore.int(Key.CONNECTION_TEST_TIMEOUT) { 3000 }
    var alwaysShowAddress by configurationStore.boolean(Key.ALWAYS_SHOW_ADDRESS)
    var blurredAddress by configurationStore.boolean(Key.BLURRED_ADDRESS)

    var providerHysteria2 by configurationStore.stringToInt(Key.PROVIDER_HYSTERIA2) { 0 }

    var tunImplementation by configurationStore.stringToInt(Key.TUN_IMPLEMENTATION) { TunImplementation.MIXED }
    var profileTrafficStatistics by configurationStore.boolean(Key.PROFILE_TRAFFIC_STATISTICS) { true }
    var certProvider by configurationStore.stringToInt(Key.CERT_PROVIDER) { CertProvider.MOZILLA }
    var ignoreDeviceIdle by configurationStore.boolean(Key.IGNORE_DEVICE_IDLE)

    var trafficDescending by configurationStore.boolean(Key.TRAFFIC_DESCENDING) { false }
    var trafficSortMode by configurationStore.int(Key.TRAFFIC_SORT_MODE) { TrafficSortMode.START }

    var speedTestUrl by configurationStore.string(Key.SPEED_TEST_URL) { SPEED_TEST_URL }
    var speedTestTimeout by configurationStore.int(Key.SPEED_TEST_TIMEOUT) { 20000 }

    // ntp
    var ntpEnable by configurationStore.boolean(Key.ENABLE_NTP) { false }
    var ntpAddress by configurationStore.string(Key.NTP_SERVER) { "time.apple.com" }
    var ntpPort by configurationStore.stringToInt(Key.NTP_PORT) { 123 }
    var ntpInterval by configurationStore.string(Key.NTP_INTERVAL) { "30m" }

    // protocol

    var uploadSpeed by configurationStore.stringToInt(Key.UPLOAD_SPEED) { 0 }
    var downloadSpeed by configurationStore.stringToInt(Key.DOWNLOAD_SPEED) { 0 }
    var customPluginPrefix by configurationStore.string(Key.CUSTOM_PLUGIN_PREFIX)

    var dirty by profileCacheStore.boolean(Key.PROFILE_DIRTY)
    var editingId by profileCacheStore.long(Key.PROFILE_ID)
    var editingGroup by profileCacheStore.long(Key.PROFILE_GROUP)
    var profileName by profileCacheStore.string(Key.PROFILE_NAME)
    var serverAddress by profileCacheStore.string(Key.SERVER_ADDRESS)
    var serverPort by profileCacheStore.stringToInt(Key.SERVER_PORT)
    var serverPorts by profileCacheStore.string(Key.SERVER_PORTS)
    var serverUsername by profileCacheStore.string(Key.SERVER_USERNAME)
    var serverPassword by profileCacheStore.string(Key.SERVER_PASSWORD)
    var serverPassword1 by profileCacheStore.string(Key.SERVER_PASSWORD1)
    var serverMethod by profileCacheStore.string(Key.SERVER_METHOD)
    var pluginName by profileCacheStore.string(Key.PLUGIN_NAME)
    var pluginConfig by profileCacheStore.string(Key.PLUGIN_CONFIG)
    var udpOverTcp by profileCacheStore.boolean(Key.UDP_OVER_TCP)

    var serverProtocol by profileCacheStore.string(Key.SERVER_PROTOCOL)
    var serverObfs by profileCacheStore.string(Key.SERVER_OBFS)

    var serverNetwork by profileCacheStore.string(Key.SERVER_V2RAY_TRANSPORT)
    var serverHost by profileCacheStore.string(Key.SERVER_HOST)
    var serverPath by profileCacheStore.string(Key.SERVER_PATH)
    var serverHeaders by profileCacheStore.string(Key.SERVER_HEADERS)
    var serverWsMaxEarlyData by profileCacheStore.stringToInt(Key.SERVER_WS_MAX_EARLY_DATA)
    var serverWsEarlyDataHeaderName by profileCacheStore.string(Key.SERVER_WS_EARLY_DATA_HEADER_NAME)
    var serverSNI by profileCacheStore.string(Key.SERVER_SNI)
    var serverSecurity by profileCacheStore.string(Key.SERVER_SECURITY)
    var serverEncryption by profileCacheStore.string(Key.SERVER_ENCRYPTION)
    var serverALPN by profileCacheStore.string(Key.SERVER_ALPN)
    var serverCertificates by profileCacheStore.string(Key.SERVER_CERTIFICATES)
    var serverPinnedCertificateChain by profileCacheStore.string(Key.SERVER_PINNED_CERTIFICATE_CHAIN)
    var serverUtlsFingerPrint by profileCacheStore.string(Key.SERVER_UTLS_FINGERPRINT)
    var serverRealityPublicKey by profileCacheStore.string(Key.SERVER_REALITY_PUBLIC_KEY)
    var serverRealityShortID by profileCacheStore.string(Key.SERVER_REALITY_SHORT_ID)
    var serverMTU by profileCacheStore.stringToInt(Key.SERVER_MTU)
    var serverAllowInsecure by profileCacheStore.boolean(Key.SERVER_ALLOW_INSECURE)
    var serverFragment by profileCacheStore.boolean(Key.SERVER_FRAGMENT)
    var serverFragmentFallbackDelay by profileCacheStore.string(Key.SERVER_FRAGMENT_FALLBACK_DELAY) { "500ms" }
    var serverRecordFragment by profileCacheStore.boolean(Key.SERVER_RECORD_FRAGMENT)

    var serverReserved by profileCacheStore.string(Key.SERVER_RESERVED)
    var localAddress by profileCacheStore.string(Key.LOCAL_ADDRESS)
    var listenPort by profileCacheStore.stringToInt(Key.LISTEN_PORT)
    var privateKey by profileCacheStore.string(Key.PRIVATE_KEY)
    var publicKey by profileCacheStore.string(Key.PUBLIC_KEY)
    var preSharedKey by profileCacheStore.string(Key.PRE_SHARED_KEY)
    var serverPersistentKeepaliveInterval by profileCacheStore.stringToInt(Key.SERVER_PERSISTENT_KEEPALIVE_INTERVAL)

    var serverMux by profileCacheStore.boolean(Key.SERVER_MUX) { false }
    var serverBrutal by profileCacheStore.boolean(Key.SERVER_BRUTAL) { false }
    var serverMuxType by profileCacheStore.stringToInt(Key.SERVER_MUX_TYPE) { MuxType.H2MUX }
    var serverMuxStrategy by profileCacheStore.stringToInt(Key.SERVER_MUX_STRATEGY) { MuxStrategy.MAX_CONNECTIONS }
    var serverMuxNumber by profileCacheStore.stringToInt(Key.SERVER_MUX_NUMBER) { 8 }
    var serverMuxPadding by profileCacheStore.boolean(Key.SERVER_MUX_PADDING) { false }

    var serverUserID by profileCacheStore.string(Key.SERVER_USER_ID)
    var serverAlterID by profileCacheStore.stringToInt(Key.SERVER_ALTER_ID)
    var serverPacketEncoding by profileCacheStore.stringToInt(Key.SERVER_PACKET_ENCODING)
    var serverAuthenticatedLength by profileCacheStore.boolean(Key.SERVER_AUTHENTICATED_LENGTH)

    var serverECH by profileCacheStore.boolean(Key.SERVER_ECH)
    var serverECHConfig by profileCacheStore.string(Key.SERVER_ECH_CONFIG)

    var serverAuthType by profileCacheStore.stringToInt(Key.SERVER_AUTH_TYPE)
    var serverStreamReceiveWindow by profileCacheStore.stringToIntIfExists(Key.SERVER_STREAM_RECEIVE_WINDOW)
    var serverConnectionReceiveWindow by profileCacheStore.stringToIntIfExists(Key.SERVER_CONNECTION_RECEIVE_WINDOW)
    var serverDisableMtuDiscovery by profileCacheStore.boolean(Key.SERVER_DISABLE_MTU_DISCOVERY)
    var serverHopInterval by profileCacheStore.string(Key.SERVER_HOP_INTERVAL) { "10s" }

    var protocolVersion by profileCacheStore.stringToInt(Key.PROTOCOL_VERSION) { 2 } // default is SOCKS5

    var serverProtocolInt by profileCacheStore.stringToInt(Key.SERVER_PROTOCOL)
    var serverPrivateKey by profileCacheStore.string(Key.SERVER_PRIVATE_KEY)
    var serverInsecureConcurrency by profileCacheStore.stringToInt(Key.SERVER_INSECURE_CONCURRENCY)
    var serverNoPostQuantum by profileCacheStore.boolean(Key.SERVER_NO_POST_QUANTUM)

    var serverUDPRelayMode by profileCacheStore.string(Key.SERVER_UDP_RELAY_MODE)
    var serverCongestionController by profileCacheStore.string(Key.SERVER_CONGESTION_CONTROLLER)
    var serverDisableSNI by profileCacheStore.boolean(Key.SERVER_DISABLE_SNI)
    var serverZeroRTT by profileCacheStore.boolean(Key.SERVER_ZERO_RTT)

    var serverInitialMTU by profileCacheStore.stringToInt(Key.SERVER_INITIAL_MTU) { 1300 }
    var serverMinimumMTU by profileCacheStore.stringToInt(Key.SERVER_MINIMUM_MTU) { 1290 }

    var serverIdleSessionCheckInterval by profileCacheStore.string(Key.SERVER_IDLE_SESSION_CHECK_INTERVAL) {
        "30s"
    }
    var serverIdleSessionTimeout by profileCacheStore.string(Key.SERVER_IDLE_SESSION_TIMEOUT) { "30s" }
    var serverMinIdleSession by profileCacheStore.stringToInt(Key.SERVER_MIN_IDLE_SESSION) { 0 }

    var serverManagement by profileCacheStore.stringToInt(Key.SERVER_MANAGEMENT)
    var serverInterruptExistConnections by profileCacheStore.boolean(Key.SERVER_INTERRUPT_EXIST_CONNECTIONS) { true }
    var serverTestURL by profileCacheStore.string(Key.SERVER_TEST_URL) { CONNECTION_TEST_URL }
    var serverTestInterval by profileCacheStore.string(Key.SERVER_TEST_INTERVAL) { "3m" }
    var serverIdleTimeout by profileCacheStore.string(Key.SERVER_IDLE_TIMEOUT) { "30m" }
    var serverTolerance by profileCacheStore.stringToInt(Key.SERVER_TOLERANCE) { 50 }
    var serverType by profileCacheStore.stringToInt(Key.SERVER_TYPE)
    var serverGroup by profileCacheStore.stringToLong(Key.SERVER_GROUP)
    var serverFilterNotRegex by profileCacheStore.string(Key.SERVER_FILTER_NOT_REGEX)
    var serverProxies by profileCacheStore.string(Key.SERVER_PROXIES)

    // Route
    var routeName by profileCacheStore.string(Key.ROUTE_NAME)
    var routeAction by profileCacheStore.string(Key.ROUTE_ACTION)

    var routeDomain by profileCacheStore.string(Key.ROUTE_DOMAIN)
    var routeIP by profileCacheStore.string(Key.ROUTE_IP)
    var routePort by profileCacheStore.string(Key.ROUTE_PORT)
    var routeSourcePort by profileCacheStore.string(Key.ROUTE_SOURCE_PORT)
    var routeNetwork by profileCacheStore.string(Key.ROUTE_NETWORK)
    var routeSource by profileCacheStore.string(Key.ROUTE_SOURCE)
    var routeProtocol by profileCacheStore.stringSet(Key.ROUTE_PROTOCOL)
    var routeClient by profileCacheStore.stringSet(Key.ROUTE_CLIENT)
    var routePackages by profileCacheStore.stringSet(Key.ROUTE_PACKAGES)
    var routeSSID by profileCacheStore.string(Key.ROUTE_SSID)
    var routeBSSID by profileCacheStore.string(Key.ROUTE_BSSID)
    var routeClashMode by profileCacheStore.string(Key.ROUTE_CLASH_MODE)
    var routeNetworkType by profileCacheStore.stringSet(Key.ROUTE_NETWORK_TYPE)
    var routeNetworkIsExpensive by profileCacheStore.boolean(Key.ROUTE_NETWORK_IS_EXPENSIVE)

    var routeOutbound by profileCacheStore.stringToInt(Key.ROUTE_OUTBOUND)
    var routeOutboundRule by profileCacheStore.long(Key.ROUTE_OUTBOUND + "Long")

    var routeOverrideAddress by profileCacheStore.string(Key.ROUTE_OVERRIDE_ADDRESS)
    var routeOverridePort by profileCacheStore.stringToInt(Key.ROUTE_OVERRIDE_PORT)
    var routeTlsFragment by profileCacheStore.boolean(Key.ROUTE_TLS_FRAGMENT)
    var routeTlsRecordFragment by profileCacheStore.boolean(Key.ROUTE_TLS_RECORD_FRAGMENT)
    var routeTlsFragmentFallbackDelay by profileCacheStore.string(Key.ROUTE_TLS_FRAGMENT_FALLBACK_DELAY)

    var routeResolveStrategy by profileCacheStore.string(Key.ROUTE_RESOLVE_STRATEGY)
    var routeResolveDisableCache by profileCacheStore.boolean(Key.ROUTE_RESOLVE_DISABLE_CACHE)
    var routeResolveRewriteTTL by profileCacheStore.int(Key.ROUTE_RESOLVE_REWRITE_TTL)
    var routeResolveClientSubnet by profileCacheStore.string(Key.ROUTE_RESOLVE_CLIENT_SUBNET)

    var routeSniffTimeout by profileCacheStore.string(Key.ROUTE_SNIFF_TIMEOUT)
    var routeSniffers by profileCacheStore.stringSet(Key.ROUTE_SNIFFERS)

    var frontProxy by profileCacheStore.long(Key.GROUP_FRONT_PROXY + "Long")
    var landingProxy by profileCacheStore.long(Key.GROUP_LANDING_PROXY + "Long")
    var frontProxyTmp by profileCacheStore.stringToInt(Key.GROUP_FRONT_PROXY)
    var landingProxyTmp by profileCacheStore.stringToInt(Key.GROUP_LANDING_PROXY)

    var serverConfig by profileCacheStore.string(Key.SERVER_CONFIG)
    var serverCustom by profileCacheStore.string(Key.SERVER_CUSTOM)
    var serverCustomOutbound by profileCacheStore.string(Key.SERVER_CUSTOM_OUTBOUND)

    var groupName by profileCacheStore.string(Key.GROUP_NAME)
    var groupType by profileCacheStore.stringToInt(Key.GROUP_TYPE)
    var groupOrder by profileCacheStore.stringToInt(Key.GROUP_ORDER)

    var subscriptionType by profileCacheStore.stringToInt(Key.SUBSCRIPTION_TYPE)
    var subscriptionToken by profileCacheStore.string(Key.SUBSCRIPTION_TOKEN)
    var subscriptionLink by profileCacheStore.string(Key.SUBSCRIPTION_LINK)
    var subscriptionForceResolve by profileCacheStore.boolean(Key.SUBSCRIPTION_FORCE_RESOLVE)
    var subscriptionDeduplication by profileCacheStore.boolean(Key.SUBSCRIPTION_DEDUPLICATION)
    var subscriptionFilterRegex by profileCacheStore.string(Key.SUBSCRIPTION_FILTER_REGEX)
    var subscriptionUpdateWhenConnectedOnly by profileCacheStore.boolean(Key.SUBSCRIPTION_UPDATE_WHEN_CONNECTED_ONLY)
    var subscriptionUserAgent by profileCacheStore.string(Key.SUBSCRIPTION_USER_AGENT)
    var subscriptionAutoUpdate by profileCacheStore.boolean(Key.SUBSCRIPTION_AUTO_UPDATE)
    var subscriptionAutoUpdateDelay by profileCacheStore.stringToInt(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY) { 360 }

    var taskerAction by profileCacheStore.stringToInt(Key.TASKER_ACTION)
    var taskerProfile by profileCacheStore.stringToInt(Key.TASKER_PROFILE)
    var taskerProfileId by profileCacheStore.long(Key.TASKER_PROFILE_ID) { -1L }

    var rulesFirstCreate by profileCacheStore.boolean(Key.RULES_FIRST_CREATE)

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
    }
}
