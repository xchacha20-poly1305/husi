package io.nekohasekai.sagernet.database

import android.os.Binder
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.CONNECTION_TEST_URL
import io.nekohasekai.sagernet.CertProvider
import io.nekohasekai.sagernet.DEFAULT_HTTP_BYPASS
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.NetworkInterfaceStrategy
import io.nekohasekai.sagernet.ProtocolProvider
import io.nekohasekai.sagernet.SPEED_TEST_URL
import io.nekohasekai.sagernet.SPEED_TEST_UPLOAD_URL
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

    var providerHysteria2 by configurationStore.stringToInt(Key.PROVIDER_HYSTERIA2) { ProtocolProvider.CORE }
    var providerJuicity by configurationStore.stringToInt(Key.PROVIDER_JUICITY) { ProtocolProvider.PLUGIN }

    var tunImplementation by configurationStore.stringToInt(Key.TUN_IMPLEMENTATION) { TunImplementation.MIXED }
    var profileTrafficStatistics by configurationStore.boolean(Key.PROFILE_TRAFFIC_STATISTICS) { true }
    var certProvider by configurationStore.stringToInt(Key.CERT_PROVIDER) { CertProvider.MOZILLA }
    var ignoreDeviceIdle by configurationStore.boolean(Key.IGNORE_DEVICE_IDLE)
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
    var ntpPort by configurationStore.stringToInt(Key.NTP_PORT) { 123 }
    var ntpInterval by configurationStore.string(Key.NTP_INTERVAL) { "30m" }

    // protocol

    var uploadSpeed by configurationStore.stringToInt(Key.UPLOAD_SPEED) { 0 }
    var downloadSpeed by configurationStore.stringToInt(Key.DOWNLOAD_SPEED) { 0 }
    var customPluginPrefix by configurationStore.string(Key.CUSTOM_PLUGIN_PREFIX)

    var editingId by profileCacheStore.long(Key.PROFILE_ID)
    var editingGroup by profileCacheStore.long(Key.PROFILE_GROUP)

    var rulesFirstCreate by profileCacheStore.boolean(Key.RULES_FIRST_CREATE)

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
    }
}
