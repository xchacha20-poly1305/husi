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

    const val ACCEPTED_LICENSE = "acceptedLicense"

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
    const val FAKE_DNS_FOR_ALL = "fakeDNSForAll"
    const val FAKE_DNS_RANGE_4 = "fakeDNSRange4"
    const val FAKE_DNS_RANGE_6 = "fakeDNSRange6"
    const val DNS_HOSTS = "dnsHosts"

    const val PROXY_APPS = "proxyApps"
    const val UPDATE_PROXY_APPS_WHEN_INSTALL = "updateProxyAppsWhenInstall"
    const val BYPASS_MODE = "bypassMode"

    // const val INDIVIDUAL = "individual"
    const val PACKAGES = "packages"
    const val METERED_NETWORK = "meteredNetwork"
    const val NETWORK_INTERFACE_STRATEGY = "networkInterfaceStrategy"
    const val NETWORK_PREFERRED_INTERFACES = "networkPreferredInterfaces"
    // const val FORCED_SEARCH_PROCESS = "forcedSearchProcess"
    const val RULES_PROVIDER = "rulesProvider"
    const val CUSTOM_RULE_PROVIDER = "customRuleProvider"

    const val BYPASS_LAN = "bypassLan"

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
    const val LOG_MAX_LINE = "logMaxLine"
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
    const val PROVIDER_NAIVE = "providerNaive"
    const val CUSTOM_PLUGIN_PREFIX = "customPluginPrefix"

    const val ACQUIRE_WAKE_LOCK = "acquireWakeLock"

    const val TUN_IMPLEMENTATION = "tunImplementation"
    const val PROFILE_TRAFFIC_STATISTICS = "profileTrafficStatistics"

    const val CERT_PROVIDER = "certProvider"
    const val DISABLE_PROCESS_TEXT = "disableProcessText"

    const val TRAFFIC_DESCENDING = "trafficDescending"
    const val TRAFFIC_SORT_MODE = "trafficSortMode"
    const val TRAFFIC_CONNECTION_QUERY = "trafficConnectionQuery"

    const val SPEED_TEST_URL = "speedTestURL"
    const val SPEED_TEST_UPLOAD_URL = "speedTestUploadURL"
    const val SPEED_TEST_UPLOAD_LENGTH = "speedTestUploadLength"
    const val SPEED_TEST_TIMEOUT = "speedTestTimeout"

    const val PROFILE_ID = "profileId"
    const val PROFILE_GROUP = "profileGroup"
    const val PROFILE_CURRENT = "profileCurrent"

    const val RULES_FIRST_CREATE = "rulesFirstCreate"

}

object NavRoutes {
    const val CONFIGURATION = "configuration"
    const val GROUPS = "groups"
    const val ROUTE = "route"
    const val SETTINGS = "settings"
    const val LOG = "log"
    const val DASHBOARD = "dashboard"
    const val TOOLS = "tools"
    const val ABOUT = "about"
    fun connectionsDetail(uuid: String) = "connections/$uuid"
    const val CONNECTIONS_DETAIL_TEMPLE = "connections/{uuid}"
}

object AlertType {
    // message: none
    const val MISSING_PLUGIN = 0
    // message: plugin name
    const val NEED_WIFI_PERMISSION = 1
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
    const val CHROME = 3
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

const val LICENSE = """Copyright (C) 2024-2026 by Husi authors <HystericalDragons@proton.me>
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
