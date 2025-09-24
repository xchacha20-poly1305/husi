package io.nekohasekai.sagernet

const val CONNECTION_TEST_URL = "http://cp.cloudflare.com/"
const val SPEED_TEST_URL = "http://speed.cloudflare.com/__down?bytes=20000000"
const val SPEED_TEST_UPLOAD_URL = "http://speed.cloudflare.com/__up"

object Key {

    const val DB_PUBLIC = "configuration.db"
    const val DB_PROFILE = "sager_net.db"

    const val GENERAL_SETTINGS = "generalSettings"
    const val ROUTE_SETTINGS = "routeSettings"
    const val PROTOCOL_SETTINGS = "protocolSettings"
    const val DNS_SETTINGS = "dnsSettings"
    const val INBOUND_SETTINGS = "inboundSettings"
    const val MISC_SETTINGS = "miscSettings"
    const val NTP_SETTINGS = "ntpSettings"

    const val PERSIST_ACROSS_REBOOT = "isAutoConnect"

    const val APP_EXPERT = "isExpert"
    const val APP_THEME = "appTheme"
    const val NIGHT_THEME = "nightTheme"
    const val APP_LANGUAGE = "appLanguage"
    const val SERVICE_MODE = "serviceMode"
    const val MODE_VPN = "vpn"
    const val MODE_PROXY = "proxy"
    const val MEMORY_LIMIT = "memoryLimit"
    const val DEBUG_LISTEN = "debugListen"
    const val NETWORK_STRATEGY = "networkStrategy"

    const val REMOTE_DNS = "remoteDns"
    const val DIRECT_DNS = "directDns"
    const val DOMAIN_STRATEGY_FOR_DIRECT = "domain_strategy_for_direct"
    const val DOMAIN_STRATEGY_FOR_SERVER = "domain_strategy_for_server"
    const val ENABLE_FAKE_DNS = "enableFakeDns"
    const val DNS_HOSTS = "dnsHosts"

    const val PROXY_APPS = "proxyApps"
    const val UPDATE_PROXY_APPS_WHEN_INSTALL = "updateProxyAppsWhenInstall"
    const val BYPASS_MODE = "bypassMode"

    // const val INDIVIDUAL = "individual"
    const val PACKAGES = "packages"
    const val METERED_NETWORK = "meteredNetwork"
    const val NETWORK_INTERFACE_STRATEGY = "networkInterfaceStrategy"
    const val NETWORK_PREFERRED_INTERFACES = "networkPreferredInterfaces"
    const val FORCED_SEARCH_PROCESS = "forcedSearchProcess"
    const val RULES_PROVIDER = "rulesProvider"
    const val CUSTOM_RULE_PROVIDER = "customRuleProvider"

    const val BYPASS_LAN = "bypassLan"
    const val BYPASS_LAN_IN_CORE = "bypassLanInCore"

    const val APPEND_HTTP_PROXY = "appendHttpProxy"
    const val HTTP_PROXY_BYPASS = "httpProxyBypass"
    const val INBOUND_USERNAME = "inboundUsername"
    const val INBOUND_PASSWORD = "inboundPassword"
    const val ANCHOR_SSID = "anchorSSID"

    const val MIXED_PORT = "mixedPort"
    const val ALLOW_ACCESS = "allowAccess"
    const val SHOW_GROUP_IN_NOTIFICATION = "showGroupInNotification"
    const val SPEED_INTERVAL = "speedInterval"
    const val SHOW_DIRECT_SPEED = "showDirectSpeed"
    const val LOCAL_DNS_PORT = "portLocalDns"

    const val CONNECTION_TEST_URL = "connectionTestURL"
    const val CONNECTION_TEST_CONCURRENT = "connectionTestConcurrent"
    const val CONNECTION_TEST_TIMEOUT = "connectionTestTimeout"

    const val SECURITY_ADVISORY = "securityAdvisory"
    const val TCP_KEEP_ALIVE_INTERVAL = "tcpKeepAliveInterval"
    const val LOG_LEVEL = "logLevel"
    const val LOG_MAX_SIZE = "logMaxSize"
    const val MTU = "mtu"
    const val ALLOW_APPS_BYPASS_VPN = "allowAppsBypassVpn"
    const val ALWAYS_SHOW_ADDRESS = "alwaysShowAddress"
    const val BLURRED_ADDRESS = "blurredAddress"

    // NTP Settings
    const val ENABLE_NTP = "ntpEnable"
    const val NTP_SERVER = "ntpAddress"
    const val NTP_PORT = "ntpPort"
    const val NTP_INTERVAL = "ntpInterval"

    // Protocol Settings
    const val UPLOAD_SPEED = "uploadSpeed"
    const val DOWNLOAD_SPEED = "downloadSpeed"
    const val PROVIDER_HYSTERIA2 = "providerHysteria2"
    const val PROVIDER_JUICITY = "providerJuicity"
    const val CUSTOM_PLUGIN_PREFIX = "customPluginPrefix"

    const val ACQUIRE_WAKE_LOCK = "acquireWakeLock"

    const val TUN_IMPLEMENTATION = "tunImplementation"
    const val PROFILE_TRAFFIC_STATISTICS = "profileTrafficStatistics"

    const val CERT_PROVIDER = "certProvider"
    const val IGNORE_DEVICE_IDLE = "ignoreDeviceIdle"
    const val DISABLE_PROCESS_TEXT = "disableProcessText"

    const val TRAFFIC_DESCENDING = "trafficDescending"
    const val TRAFFIC_SORT_MODE = "trafficSortMode"
    const val TRAFFIC_CONNECTION_QUERY = "trafficConnectionQuery"

    const val SPEED_TEST_URL = "speedTestURL"
    const val SPEED_TEST_UPLOAD_URL = "speedTestUploadURL"
    const val SPEED_TEST_UPLOAD_LENGTH = "speedTestUploadLength"
    const val SPEED_TEST_TIMEOUT = "speedTestTimeout"

    const val PROFILE_DIRTY = "profileDirty"
    const val PROFILE_ID = "profileId"
    const val PROFILE_NAME = "profileName"
    const val PROFILE_GROUP = "profileGroup"
    const val PROFILE_CURRENT = "profileCurrent"

    const val SERVER_ADDRESS = "serverAddress"
    const val SERVER_PORT = "serverPort"
    const val SERVER_PORTS = "serverPorts"
    const val SERVER_USERNAME = "serverUsername"
    const val SERVER_PASSWORD = "serverPassword"
    const val SERVER_METHOD = "serverMethod"
    const val SERVER_PASSWORD1 = "serverPassword1"
    const val PLUGIN_NAME = "pluginName"
    const val PLUGIN_CONFIG = "pluginConfig"
    const val UDP_OVER_TCP = "udpOverTcp"

    const val PROTOCOL_VERSION = "protocolVersion"

    const val SERVER_PROTOCOL = "serverProtocol"
    const val SERVER_OBFS = "serverObfs"

    const val SERVER_V2RAY_TRANSPORT = "serverV2rayTransport"
    const val SERVER_HOST = "serverHost"
    const val SERVER_PATH = "serverPath"
    const val SERVER_HEADERS = "serverHeaders"
    const val SERVER_WS_MAX_EARLY_DATA = "serverWsMaxEarlyData"
    const val SERVER_WS_EARLY_DATA_HEADER_NAME = "serverWsEarlyDataHeaderName"
    const val SERVER_SECURITY = "serverSecurity"
    const val SERVER_SNI = "serverSNI"
    const val SERVER_ENCRYPTION = "serverEncryption"
    const val SERVER_ALPN = "serverALPN"
    const val SERVER_CERTIFICATES = "serverCertificates"
    const val SERVER_CERT_PUBLIC_KEY_SHA256 = "serverCertPublicKeySha256"
    const val SERVER_M_TLS_CERT = "serverMTlsCert"
    const val SERVER_M_TLS_KEY = "serverMTlsKey"
    const val SERVER_PINNED_CERTIFICATE_CHAIN = "serverPinnedCertificateChain"
    const val SERVER_UTLS_FINGERPRINT = "serverUtlsFingerprint"
    const val SERVER_REALITY_PUBLIC_KEY = "serverRealityPublicKey"
    const val SERVER_REALITY_SHORT_ID = "serverRealityShortID"
    const val SERVER_MTU = "serverMTU"
    const val SERVER_FRAGMENT = "serverFragment"
    const val SERVER_FRAGMENT_FALLBACK_DELAY = "serverFragmentFallbackDelay"
    const val SERVER_RECORD_FRAGMENT = "serverRecordFragment"

    const val SERVER_MUX = "serverMux"
    const val SERVER_BRUTAL = "serverBrutal"
    const val SERVER_MUX_TYPE = "serverMuxType"
    const val SERVER_MUX_STRATEGY = "serverMuxStrategy"
    const val SERVER_MUX_NUMBER = "serverMuxNumber"
    const val SERVER_MUX_PADDING = "serverMuxPadding"

    const val SERVER_USER_ID = "serverUserID"
    const val SERVER_ALTER_ID = "serverAlterID"
    const val SERVER_PACKET_ENCODING = "serverPacketEncoding"
    const val SERVER_VMESS_EXPERIMENTS_CATEGORY = "serverVMessExperimentsCategory"
    const val SERVER_AUTHENTICATED_LENGTH = "serverAuthenticatedLength"

    const val SERVER_INITIAL_MTU = "serverInitialMTU"
    const val SERVER_MINIMUM_MTU = "serverMinimumMTU"

    const val SERVER_CONFIG = "serverConfig"
    const val SERVER_CUSTOM = "serverCustom"
    const val SERVER_CUSTOM_OUTBOUND = "serverCustomOutbound"

    const val SERVER_SECURITY_CATEGORY = "serverSecurityCategory"
    const val SERVER_TLS_CAMOUFLAGE_CATEGORY = "serverTlsCamouflageCategory"
    const val SERVER_M_TLS_CATEGORY = "serverMTlsCategory"
    const val SERVER_ECH_CATEGORY = "serverEchCategory"
    const val SERVER_WS_CATEGORY = "serverWsCategory"
    const val SERVER_MUX_CATEGORY = "serverMuxCategory"
    const val SERVER_ALLOW_INSECURE = "serverAllowInsecure"

    const val SERVER_ECH = "serverECH"
    const val SERVER_ECH_CONFIG = "serverECHConfig"

    const val SERVER_AUTH_TYPE = "serverAuthType"
    const val SERVER_STREAM_RECEIVE_WINDOW = "serverStreamReceiveWindow"
    const val SERVER_CONNECTION_RECEIVE_WINDOW = "serverConnectionReceiveWindow"
    const val SERVER_DISABLE_MTU_DISCOVERY = "serverDisableMtuDiscovery"
    const val SERVER_HOP_INTERVAL = "hopInterval"

    const val SERVER_PRIVATE_KEY = "serverPrivateKey"
    const val SERVER_INSECURE_CONCURRENCY = "serverInsecureConcurrency"
    const val SERVER_NO_POST_QUANTUM = "serverNoPostQuantum"

    const val SERVER_UDP_RELAY_MODE = "serverUDPRelayMode"
    const val SERVER_CONGESTION_CONTROLLER = "serverCongestionController"
    const val SERVER_DISABLE_SNI = "serverDisableSNI"
    const val SERVER_ZERO_RTT = "serverZeroRTT"

    const val SERVER_IDLE_SESSION_CHECK_INTERVAL = "serverIdleSessionCheckInterval"
    const val SERVER_IDLE_SESSION_TIMEOUT = "serverIdleSessionTimeout"
    const val SERVER_MIN_IDLE_SESSION = "serverMinIdleSession"

    const val SERVER_RESERVED = "serverReserved"
    const val LOCAL_ADDRESS = "localAddress"
    const val LISTEN_PORT = "listenPort"
    const val PRIVATE_KEY = "privateKey"
    const val PUBLIC_KEY = "publicKey"
    const val PRE_SHARED_KEY = "preSharedKey"
    const val SERVER_PERSISTENT_KEEPALIVE_INTERVAL = "serverPersistentKeepaliveInterval"

    const val SERVER_MANAGEMENT = "serverManagement"
    const val SERVER_INTERRUPT_EXIST_CONNECTIONS = "serverInterruptExistConnections"
    const val SERVER_TEST_URL = "serverTestURL"
    const val SERVER_TEST_INTERVAL = "serverTestInterval"
    const val SERVER_IDLE_TIMEOUT = "serverIdleTimeout"
    const val SERVER_TOLERANCE = "serverTolerance"
    const val SERVER_TYPE = "serverType"
    const val SERVER_GROUP = "serverGroup"
    const val SERVER_FILTER_NOT_REGEX = "serverFilterNotRegex"
    const val SERVER_PROXIES = "serverProxies"

    // Route
    const val ROUTE_NAME = "routeName"
    const val ROUTE_ACTION = "routeAction"

    // common rules
    const val ROUTE_DOMAIN = "routeDomain"
    const val ROUTE_IP = "routeIP"
    const val ROUTE_PORT = "routePort"
    const val ROUTE_SOURCE_PORT = "routeSourcePort"
    const val ROUTE_NETWORK = "routeNetwork"
    const val ROUTE_SOURCE = "routeSource"
    const val ROUTE_PROTOCOL = "routeProtocol"
    const val ROUTE_CLIENT = "routeClient"
    const val ROUTE_PACKAGES = "routePackages"
    const val ROUTE_SSID = "routeSSID"
    const val ROUTE_BSSID = "routeBSSID"
    const val ROUTE_CLASH_MODE = "routeClashMode"
    const val ROUTE_NETWORK_TYPE = "routeNetworkType"
    const val ROUTE_NETWORK_IS_EXPENSIVE = "routeNetworkIsExpensive"

    // Action.route
    const val ROUTE_ACTION_ROUTE = "routeActionRoute"
    const val ROUTE_OUTBOUND = "routeOutbound"

    // Action.route-options
    const val ROUTE_ACTION_ROUTE_OPTIONS = "routeActionRouteOptions"
    const val ROUTE_OVERRIDE_ADDRESS = "routeOverrideAddress"
    const val ROUTE_OVERRIDE_PORT = "routeOverridePort"
    const val ROUTE_TLS_FRAGMENT = "routeTlsFragment"
    const val ROUTE_TLS_RECORD_FRAGMENT = "routeTlsRecordFragment"
    const val ROUTE_TLS_FRAGMENT_FALLBACK_DELAY = "routeTlsFragmentFallbackDelay"

    // Action.resolve
    const val ROUTE_ACTION_RESOLVE_OPTIONS = "routeActionResolveOptions"
    const val ROUTE_RESOLVE_STRATEGY = "routeResolveStrategy"
    const val ROUTE_RESOLVE_DISABLE_CACHE = "routeResolveDisableCache"
    const val ROUTE_RESOLVE_REWRITE_TTL = "routeResolveRewriteTTL"
    const val ROUTE_RESOLVE_CLIENT_SUBNET = "routeResolveClientSubnet"

    // Action.sniff
    const val ROUTE_ACTION_SNIFF_OPTIONS = "routeActionSniffOptions"
    const val ROUTE_SNIFF_TIMEOUT = "routeSniffTimeout"
    const val ROUTE_SNIFFERS = "routeSniffers"

    const val RULES_FIRST_CREATE = "rulesFirstCreate"


    const val ASSET_NAME = "assetName"
    const val ASSET_URL = "assetUrl"

    const val GROUP_NAME = "groupName"
    const val GROUP_TYPE = "groupType"
    const val GROUP_ORDER = "groupOrder"
    const val GROUP_FRONT_PROXY = "groupFrontProxy"
    const val GROUP_LANDING_PROXY = "groupLandingProxy"

    const val GROUP_SUBSCRIPTION = "groupSubscription"
    const val SUBSCRIPTION_TYPE = "subscriptionType"
    const val SUBSCRIPTION_TOKEN = "subscriptionToken"
    const val SUBSCRIPTION_LINK = "subscriptionLink"
    const val SUBSCRIPTION_FORCE_RESOLVE = "subscriptionForceResolve"
    const val SUBSCRIPTION_DEDUPLICATION = "subscriptionDeduplication"
    const val SUBSCRIPTION_FILTER_REGEX = "subscriptionFilterRegex"
    const val SUBSCRIPTION_UPDATE = "subscriptionUpdate"
    const val SUBSCRIPTION_UPDATE_WHEN_CONNECTED_ONLY = "subscriptionUpdateWhenConnectedOnly"
    const val SUBSCRIPTION_USER_AGENT = "subscriptionUserAgent"
    const val SUBSCRIPTION_AUTO_UPDATE = "subscriptionAutoUpdate"
    const val SUBSCRIPTION_AUTO_UPDATE_DELAY = "subscriptionAutoUpdateDelay"

    const val TASKER_ACTION = "taskerAction"
    const val TASKER_PROFILE = "taskerProfile"
    const val TASKER_PROFILE_ID = "taskerProfileLong"
}

fun logLevelString(level: Int): String = when (level) {
    0 -> "panic"
    1 -> "fatal"
    2 -> "error"
    3 -> "warn"
    4 -> "info"
    5 -> "debug"
    6 -> "trace"
    else -> "info"
}

object TunImplementation {
    const val GVISOR = 0
    const val SYSTEM = 1
    const val MIXED = 2
}

object GroupType {
    const val BASIC = 0
    const val SUBSCRIPTION = 1
}

object SubscriptionType {
    const val RAW = 0
    const val OOCv1 = 1
    const val SIP008 = 2
}

object GroupOrder {
    const val ORIGIN = 0
    const val BY_NAME = 1
    const val BY_DELAY = 2
}

object MuxType {
    const val H2MUX = 0
    const val SMUX = 1
    const val YAMUX = 2
}

object MuxStrategy {
    const val MAX_CONNECTIONS = 0
    const val MIN_STREAMS = 1
    const val MAX_STREAMS = 2
}

object Action {
    const val SERVICE = "io.nekohasekai.sagernet.SERVICE"
    const val CLOSE = "io.nekohasekai.sagernet.CLOSE"
    const val RELOAD = "io.nekohasekai.sagernet.RELOAD"

    // const val SWITCH_WAKE_LOCK = "io.nekohasekai.sagernet.SWITCH_WAKELOCK"
    const val RESET_UPSTREAM_CONNECTIONS = "io.nekohasekai.sagernet.RESET_UPSTREAM_CONNECTIONS"
}

object TrafficSortMode {
    const val START = 0
    const val INBOUND = 1
    const val SRC = 2
    const val DST = 3
    const val UPLOAD = 4
    const val DOWNLOAD = 5
    const val MATCHED_RULE = 6

    val values
        get() = listOf(
            START,
            INBOUND,
            SRC,
            DST,
            UPLOAD,
            DOWNLOAD,
            MATCHED_RULE,
        )
}

object RuleProvider {
    const val OFFICIAL = 0
    const val LOYALSOLDIER = 1
    const val CHOCOLATE4U = 2
    const val CUSTOM = 3

    fun hasUnstableBranch(provider: Int): Boolean {
        return provider in OFFICIAL..LOYALSOLDIER
    }
}

object NetworkInterfaceStrategy {
    const val DEFAULT = 0
    const val HYBRID = 1
    const val FALLBACK = 2
}

object CertProvider {
    const val SYSTEM = 0
    const val MOZILLA = 1
    const val SYSTEM_AND_USER = 2 // Put it last because Go may fix the bug one day.
}

object ProtocolProvider {
    const val CORE = 0
    const val PLUGIN = 1
}

// https://github.com/chen08209/FlClash/blob/6c27f2e2f1ac033e62f09b7b30b2710dd0d13bb4/lib/models/config.dart#L110-L128
const val DEFAULT_HTTP_BYPASS = """# If you are annoyed with default value, just set a "#"
# Chinese apps that can't work with http proxy
*zhihu.com
*zhimg.com
*jd.com
100ime-iat-api.xfyun.cn
*360buyimg.com
# local
localhost
*.local
127.*
10.*
172.16.*
172.17.*
172.18.*
172.19.*
172.2*
172.30.*
172.31.*
192.168.*"""

const val LICENSE = """Copyright (C) 2024-2025 by Husi authors <HystericalDragons@proton.me>
Copyright (C) 2023 by AntiNeko authors <HystericalDragon@protomail.com>
Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>

This program is free software: you can
redistribute it and/or modify it under
the terms of the GNU General Public License
as published by the Free Software Foundation,
either version 3 of the License,
or (at your option) any later version.

This program is distributed in the hope
that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.

You should have received a copy of the
GNU General Public License along with this program.
If not, see <http://www.gnu.org/licenses/>.

In addition, no derivative work may
use the name or imply association with
this application without prior consent.
"""