package fr.husi.database

import fr.husi.CONNECTION_TEST_URL
import fr.husi.CertProvider
import fr.husi.DEFAULT_HTTP_BYPASS
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
import fr.husi.database.preference.createConfigurationDataStore
import fr.husi.ktx.boolean
import fr.husi.ktx.int
import fr.husi.ktx.long
import fr.husi.ktx.parsePort
import fr.husi.ktx.string
import fr.husi.ktx.stringSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

object DataStore {

    // share service state in main & bg process
    @Volatile
    var serviceState = ServiceState.Idle

    val configurationStore = DataStorePreferenceDataStore.create(createConfigurationDataStore())

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

    fun currentGroupId(): Long {
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) return currentSelected
        val groupId = ProfileManager.ensureDefaultGroupId()
        selectedGroup = groupId
        return groupId
    }

    fun currentGroup(): ProxyGroup {
        var group: ProxyGroup? = null
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) {
            group = runBlocking {
                SagerDatabase.groupDao.getById(currentSelected).firstOrNull()
            }
        }
        if (group != null) return group
        val groupId = ProfileManager.ensureDefaultGroupId()
        group = runBlocking {
            SagerDatabase.groupDao.getById(groupId).firstOrNull()
                ?: SagerDatabase.groupDao.allGroups().first().first()
        }
        selectedGroup = group.id
        return group
    }

    fun selectedGroupForImport(): Long {
        val current = currentGroup()
        if (current.type == GroupType.BASIC) return current.id
        val groups = runBlocking {
            SagerDatabase.groupDao.allGroups().first()
        }
        return groups.find { it.type == GroupType.BASIC }!!.id
    }

    var isExpert by configurationStore.boolean(Key.APP_EXPERT)
    var appTheme by configurationStore.int(Key.APP_THEME) { DEFAULT }
    var nightTheme by configurationStore.int(Key.NIGHT_THEME)
    var appLanguage by configurationStore.string(Key.APP_LANGUAGE)
    var serviceMode by configurationStore.string(Key.SERVICE_MODE) { Key.MODE_VPN }
    var debugListen by configurationStore.string(Key.DEBUG_LISTEN)
    var networkStrategy by configurationStore.string(Key.NETWORK_STRATEGY)
    var anchorSSID by configurationStore.string(Key.ANCHOR_SSID)

    var networkInterfaceType by configurationStore.int(Key.NETWORK_INTERFACE_STRATEGY) {
        NetworkInterfaceStrategy.DEFAULT
    }
    var networkPreferredInterfaces by configurationStore.stringSet(Key.NETWORK_PREFERRED_INTERFACES)
    // var forcedSearchProcess by configurationStore.boolean(Key.FORCED_SEARCH_PROCESS) { false }

    //    var tcpKeepAliveInterval by configurationStore.int(Key.TCP_KEEP_ALIVE_INTERVAL) { 15 }
    var mtu by configurationStore.int(Key.MTU) { 9000 }
    var tunStrictRoute by configurationStore.boolean(Key.TUN_STRICT_ROUTE) { true }
    var allowAppsBypassVpn by configurationStore.boolean(Key.ALLOW_APPS_BYPASS_VPN) { false }

    var bypassLan by configurationStore.boolean(Key.BYPASS_LAN) { true }
    var inboundUsername by configurationStore.string(Key.INBOUND_USERNAME) { "" }
    var inboundPassword by configurationStore.string(Key.INBOUND_PASSWORD) { "" }

    var allowAccess by configurationStore.boolean(Key.ALLOW_ACCESS)
    var speedInterval by configurationStore.int(Key.SPEED_INTERVAL) { 1000 }
    var showGroupInNotification by configurationStore.boolean(Key.SHOW_GROUP_IN_NOTIFICATION)

    var remoteDns by configurationStore.string(Key.REMOTE_DNS) { "tcp://dns.google" }
    var directDns by configurationStore.string(Key.DIRECT_DNS) { "local" }
    var domainStrategyForDirect by configurationStore.string(Key.DOMAIN_STRATEGY_FOR_DIRECT)
    var domainStrategyForServer by configurationStore.string(Key.DOMAIN_STRATEGY_FOR_SERVER)
    var enableFakeDns by configurationStore.boolean(Key.ENABLE_FAKE_DNS) { false }
    var fakeDNSForAll by configurationStore.boolean(Key.FAKE_DNS_FOR_ALL) { false }

    // https://developer.chrome.com/blog/local-network-access
    // Use the address belongs to these "local" networks
    // (https://wicg.github.io/local-network-access/#non-public-ip-address-blocks)
    // will make permission warning in Chrome.
    // To avoid user agreeing plenty of permissions, we decide to use these new address.
    // The pre-defined IPv4 range is limited, change to whatever user like.
    var fakeDNSRange4 by configurationStore.string(Key.FAKE_DNS_RANGE_4) { "198.51.100.0/24" }
    var fakeDNSRange6 by configurationStore.string(Key.FAKE_DNS_RANGE_6) { "2001:2::/48" }
    var dnsHosts by configurationStore.string(Key.DNS_HOSTS)

    var securityAdvisory by configurationStore.boolean(Key.SECURITY_ADVISORY) { true }
    var rulesProvider by configurationStore.int(Key.RULES_PROVIDER)
    var customRuleProvider by configurationStore.string(Key.CUSTOM_RULE_PROVIDER)
    var logLevel by configurationStore.int(Key.LOG_LEVEL) { 3 /* WARN */ }
    var logMaxLine by configurationStore.int(Key.LOG_MAX_LINE) { 1024 }
    var acquireWakeLock by configurationStore.boolean(Key.ACQUIRE_WAKE_LOCK)

    // hopefully hashCode = mHandle doesn't change, currently this is true from KitKat to Nougat
    private val userIndex by lazy { callingUserIndex() }
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

    var persistAcrossReboot by configurationStore.boolean(Key.PERSIST_ACROSS_REBOOT) { false }

    var appendHttpProxy by configurationStore.boolean(Key.APPEND_HTTP_PROXY)
    var httpProxyBypass by configurationStore.string(Key.HTTP_PROXY_BYPASS) { DEFAULT_HTTP_BYPASS }
    var connectionTestURL by configurationStore.string(Key.CONNECTION_TEST_URL) { CONNECTION_TEST_URL }
    var connectionTestConcurrent by configurationStore.int(Key.CONNECTION_TEST_CONCURRENT) { 5 }
    var connectionTestTimeout by configurationStore.int(Key.CONNECTION_TEST_TIMEOUT) { 3000 }
    var alwaysShowAddress by configurationStore.boolean(Key.ALWAYS_SHOW_ADDRESS)
    var blurredAddress by configurationStore.boolean(Key.BLURRED_ADDRESS)

    var providerHysteria2 by configurationStore.int(Key.PROVIDER_HYSTERIA2) { ProtocolProvider.CORE }
    var providerJuicity by configurationStore.int(Key.PROVIDER_JUICITY) { ProtocolProvider.PLUGIN }
    var providerNaive by configurationStore.int(Key.PROVIDER_NAIVE) { ProtocolProvider.CORE }

    var tunImplementation by configurationStore.int(Key.TUN_IMPLEMENTATION) { TunImplementation.MIXED }
    var profileTrafficStatistics by configurationStore.boolean(Key.PROFILE_TRAFFIC_STATISTICS) { true }
    var certProvider by configurationStore.int(Key.CERT_PROVIDER) { CertProvider.MOZILLA }
    var disableProcessText by configurationStore.boolean(Key.DISABLE_PROCESS_TEXT)

    var trafficDescending by configurationStore.boolean(Key.TRAFFIC_DESCENDING) { false }
    var trafficSortMode by configurationStore.int(Key.TRAFFIC_SORT_MODE) { TrafficSortMode.START }
    var trafficConnectionQuery by configurationStore.int(Key.TRAFFIC_CONNECTION_QUERY) { 1 shl 0 }

    var speedTestUrl by configurationStore.string(Key.SPEED_TEST_URL) { SPEED_TEST_URL }
    var speedTestUploadURL by configurationStore.string(Key.SPEED_TEST_UPLOAD_URL) { SPEED_TEST_UPLOAD_URL }
    var speedTestUploadLength by configurationStore.long(Key.SPEED_TEST_UPLOAD_LENGTH) { 10 * 1024 * 1024 }
    var speedTestTimeout by configurationStore.int(Key.SPEED_TEST_TIMEOUT) { 20000 }

    // ntp
    var ntpEnable by configurationStore.boolean(Key.ENABLE_NTP) { false }
    var ntpAddress by configurationStore.string(Key.NTP_SERVER) { "time.apple.com" }
    var ntpPort by configurationStore.int(Key.NTP_PORT) { 123 }
    var ntpInterval by configurationStore.string(Key.NTP_INTERVAL) { "30m" }

    // protocol

    var uploadSpeed by configurationStore.int(Key.UPLOAD_SPEED) { 0 }
    var downloadSpeed by configurationStore.int(Key.DOWNLOAD_SPEED) { 0 }
    var customPluginPrefix by configurationStore.string(Key.CUSTOM_PLUGIN_PREFIX)

    var rulesFirstCreate by configurationStore.boolean(Key.RULES_FIRST_CREATE)

}
