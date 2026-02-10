@file:Suppress(
    "unused", "ConstPropertyName", "PropertyName", "ClassName", "RemoveEmptyClassBody",
    "SpellCheckingInspection",
)

package fr.husi.fmt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable as KxsSerializable
import kotlinx.serialization.json.JsonElement

object SingBoxOptions {
    // Generate on line +400

    const val NetworkTCP = "tcp"
    const val NetworkUDP = "udp"
    const val NetworkICMP = "icmp"

    const val RULE_SET_FORMAT_BINARY = "binary"
    const val RULE_SET_TYPE_REMOTE = "remote"
    const val RULE_SET_TYPE_LOCAL = "local"

    const val TYPE_TUN = "tun"
    const val TYPE_SELECTOR = "selector"
    const val TYPE_URLTEST = "urltest"
    const val TYPE_MIXED = "mixed"
    const val TYPE_DIRECT = "direct"
    const val TYPE_BLOCK = "block"
    const val TYPE_HTTP = "http"
    const val TYPE_HYSTERIA = "hysteria"
    const val TYPE_HYSTERIA2 = "hysteria2"
    const val TYPE_SHADOWSOCKS = "shadowsocks"
    const val TYPE_SOCKS = "socks"
    const val TYPE_SSH = "ssh"
    const val TYPE_TROJAN = "trojan"
    const val TYPE_TUIC = "tuic"
    const val TYPE_JUICITY = "juicity"
    const val TYPE_VMESS = "vmess"
    const val TYPE_VLESS = "vless"
    const val TYPE_WIREGUARD = "wireguard"
    const val TYPE_SHADOWTLS = "shadowtls"
    const val TYPE_ANYTLS = "anytls"
    const val TYPE_NAIVE = "naive"
    const val TYPE_TRUST_TUNNEL = "trusttunnel"

    const val TRANSPORT_WS = "ws"
    const val TRANSPORT_HTTPUPGRADE = "httpupgrade"
    const val TRANSPORT_HTTP = "http"
    const val TRANSPORT_QUIC = "quic"
    const val TRANSPORT_GRPC = "grpc"

    const val TYPE_LOGICAL = "logical"

    const val ACTION_ROUTE = "route"
    const val ACTION_ROUTE_OPTIONS = "route-options"
    const val ACTION_REJECT = "reject"
    const val ACTION_HIJACK_DNS = "hijack-dns"
    const val ACTION_SNIFF = "sniff"
    const val ACTION_RESOLVE = "resolve"

    const val LOGICAL_OR = "or"
    const val LOGICAL_AND = "and"

    const val SNIFF_HTTP = "http"
    const val SNIFF_TLS = "tls"
    const val SNIFF_QUIC = "quic"
    const val SNIFF_STUN = "stun"
    const val SNIFF_DNS = "dns"
    const val SNIFF_BITTORRENT = "bittorrent"
    const val SNIFF_DTLS = "dtls"
    const val SNIFF_SSH = "ssh"
    const val SNIFF_RDP = "rdp"
    const val SNIFF_NTP = "ntp"

    const val STRATEGY_PREFER_IPV6 = "prefer_ipv6"
    const val STRATEGY_PREFER_IPV4 = "prefer_ipv4"
    const val STRATEGY_IPV4_ONLY = "ipv4_only"
    const val STRATEGY_IPV6_ONLY = "ipv6_only"

    const val STRATEGY_DEFAULT = "default"
    const val STRATEGY_HYBRID = "hybrid"
    const val STRATEGY_FALLBACK = "fallback"

    // Custom for URL, not belongs to box.
    const val DNS_TYPE_LOCAL = "local"
    const val DNS_TYPE_UDP = "udp"
    const val DNS_TYPE_TCP = "tcp"
    const val DNS_TYPE_TLS = "tls"
    const val DNS_TYPE_HTTPS = "https"
    const val DNS_TYPE_QUIC = "quic"
    const val DNS_TYPE_H3 = "h3"
    const val DNS_TYPE_PREDEFINED = "predefined"
    const val DNS_TYPE_RCODE = "rcode"
    const val DNS_TYPE_FAKEIP = "fakeip"
    const val DNS_TYPE_HOSTS = "hosts"

    const val NETWORK_TYPE_WIFI = "wifi"
    const val NETWORK_TYPE_CELLULAR = "cellular"
    const val NETWORK_TYPE_ETHERNET = "ethernet"
    const val NETWORK_TYPE_OTHER = "other"

    const val FINGERPRINT_CHROME = "chrome"
    const val FINGERPRINT_FIREFOX = "firefox"
    const val FINGERPRINT_EDGE = "edge"
    const val FINGERPRINT_SAFARI = "safari"
    const val FINGERPRINT_360 = "360"
    const val FINGERPRINT_QQ = "qq"
    const val FINGERPRINT_IOS = "ios"
    const val FINGERPRINT_ANDROID = "android"
    const val FINGERPRINT_RANDOM = "random"
    const val FINGERPRINT_RANDOMIZED = "randomized"

    // base

    @KxsSerializable
    open class SingBoxOption {
    }

    // custom classes

    @KxsSerializable
    open class User {
        @JvmField
        var username: String? = null

        @JvmField
        var password: String? = null
    }

    open class MyOptions : SingBoxOption() {
        @JvmField
        var log: LogOptions? = null

        @JvmField
        var dns: MyDNSOptions? = null

        @JvmField
        var ntp: NTPOptions? = null

        @JvmField
        var inbounds: MutableList<Inbound>? = null

        @JvmField
        var outbounds: MutableList<MutableMap<String, Any?>>? = null

        @JvmField
        var endpoints: MutableList<MutableMap<String, Any?>>? = null

        @JvmField
        var route: MyRouteOptions? = null

        @JvmField
        var experimental: ExperimentalOptions? = null

        // public MITMOptions mitm;

        // public List<Script> scripts;
    }

    open class MyDNSOptions : SingBoxOption() {

        // Generate note: nested type RawDNSOptions
        @JvmField
        var servers: MutableList<NewDNSServerOptions>? = null

        @JvmField
        var rules: MutableList<MutableMap<String, Any?>>? = null

        @SerialName("final")
        @JvmField
        var final_: String? = null

        @JvmField
        var reverse_mapping: Boolean? = null

        // Generate note: nested type DNSClientOptions
        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var disable_expire: Boolean? = null

        @JvmField
        var independent_cache: Boolean? = null

        @JvmField
        var cache_capacity: Int? = null

        @JvmField
        var client_subnet: String? = null

    }

    open class MyRouteOptions : SingBoxOption() {

        @JvmField
        var rules: MutableList<MutableMap<String, Any?>>? = null

        @JvmField
        var rule_set: MutableList<RuleSet>? = null

        @SerialName("final")
        @JvmField
        var final_: String? = null

        @JvmField
        var find_process: Boolean? = null

        @JvmField
        var auto_detect_interface: Boolean? = null

        @JvmField
        var override_android_vpn: Boolean? = null

        @JvmField
        var default_interface: String? = null

        @JvmField
        var default_mark: Int? = null

        @JvmField
        var default_domain_resolver: DomainResolveOptions? = null

        @JvmField
        var default_network_strategy: String? = null

        @JvmField
        var default_network_type: MutableList<String>? = null

        @JvmField
        var default_fallback_network_type: MutableList<String>? = null

        @JvmField
        var default_fallback_delay: String? = null

    }

    // Classes have optional field
    // Generated in line + 163

    @KxsSerializable
    open class Inbound : SingBoxOption() {

        @JvmField
        var type: String? = null

        @JvmField
        var tag: String? = null

        // Generate note: option type:  public TunInboundOptions TunOptions;

        // Generate note: option type:  public RedirectInboundOptions RedirectOptions;

        // Generate note: option type:  public TProxyInboundOptions TProxyOptions;

        // Generate note: option type:  public DirectInboundOptions DirectOptions;

        // Generate note: option type:  public SocksInboundOptions SocksOptions;

        // Generate note: option type:  public HTTPMixedInboundOptions HTTPOptions;

        // Generate note: option type:  public HTTPMixedInboundOptions MixedOptions;

        // Generate note: option type:  public ShadowsocksInboundOptions ShadowsocksOptions;

        // Generate note: option type:  public VMessInboundOptions VMessOptions;

        // Generate note: option type:  public TrojanInboundOptions TrojanOptions;

        // Generate note: option type:  public NaiveInboundOptions NaiveOptions;

        // Generate note: option type:  public HysteriaInboundOptions HysteriaOptions;

        // Generate note: option type:  public ShadowTLSInboundOptions ShadowTLSOptions;

        // Generate note: option type:  public VLESSInboundOptions VLESSOptions;

        // Generate note: option type:  public TUICInboundOptions TUICOptions;

        // Generate note: option type:  public Hysteria2InboundOptions Hysteria2Options;

    }

    @KxsSerializable
    open class Outbound : SingBoxOption() {

        @JvmField
        var type: String? = null

        @JvmField
        var tag: String? = null

        // Generate note: option type:  public DirectOutboundOptions DirectOptions;

        // Generate note: option type:  public SocksOutboundOptions SocksOptions;

        // Generate note: option type:  public HTTPOutboundOptions HTTPOptions;

        // Generate note: option type:  public ShadowsocksOutboundOptions ShadowsocksOptions;

        // Generate note: option type:  public VMessOutboundOptions VMessOptions;

        // Generate note: option type:  public TrojanOutboundOptions TrojanOptions;

        // Generate note: option type:  public WireGuardOutboundOptions WireGuardOptions;

        // Generate note: option type:  public HysteriaOutboundOptions HysteriaOptions;

        // Generate note: option type:  public TorOutboundOptions TorOptions;

        // Generate note: option type:  public SSHOutboundOptions SSHOptions;

        // Generate note: option type:  public ShadowTLSOutboundOptions ShadowTLSOptions;

        // Generate note: option type:  public ShadowsocksROutboundOptions ShadowsocksROptions;

        // Generate note: option type:  public VLESSOutboundOptions VLESSOptions;

        // Generate note: option type:  public TUICOutboundOptions TUICOptions;

        // Generate note: option type:  public Hysteria2OutboundOptions Hysteria2Options;

        // Generate note: option type:  public SelectorOutboundOptions SelectorOptions;

        // Generate note: option type:  public URLTestOutboundOptions URLTestOptions;

    }

    @KxsSerializable
    open class Endpoint : Outbound() {

        // Generate note: option type:  public WireGuardEndpointOptions WireGuardEndpointOptions;

    }

    @KxsSerializable
    open class Rule : SingBoxOption() {

        @JvmField
        var type: String? = null

        // Generate note: option type:  public DefaultRule DefaultOptions;

        // Generate note: option type:  public LogicalRule LogicalOptions;

    }

    @KxsSerializable
    open class DNSRule : SingBoxOption() {

        @JvmField
        var type: String? = null

        // Generate note: option type:  public DefaultDNSRule DefaultOptions;

        // Generate note: option type:  public LogicalDNSRule LogicalOptions;

    }

    @KxsSerializable
    open class V2RayTransportOptions : SingBoxOption() {

        @JvmField
        var type: String? = null

        // Generate note: option type:  public V2RayHTTPOptions HTTPOptions;

        // Generate note: option type:  public V2RayWebsocketOptions WebsocketOptions;

        // Generate note: option type:  public V2RayQUICOptions QUICOptions;

        // Generate note: option type:  public V2RayGRPCOptions GRPCOptions;

        // Generate note: option type:  public V2RayHTTPUpgradeOptions HTTPUpgradeOptions;
    }


    @KxsSerializable
    open class NewDNSServerOptions : SingBoxOption() {

        @JvmField
        var type: String? = null

        @JvmField
        var tag: String? = null

    }

//    public static class RuleAction extends SingBoxOptions {
//
//        public String action;
//

    /// /        public RouteActionOptions RouteActionOptions;
    /// /
    /// /        public RouteOptionsActionOptions RouteOptionsActionOptions;
    /// /
    /// /        public DirectActionOptions DirectActionOptions;
    /// /
    /// /        public RejectActionOptions RejectActionOptions;
    /// /
    /// /        public RouteActionSniff RouteActionSniff;
    /// /
    /// /        public RouteActionResolve RouteActionResolve;
//
//    }

    @KxsSerializable
    open class Service : SingBoxOption() {

        @JvmField
        var type: String? = null

        @JvmField
        var tag: String? = null

    }

    // Paste generate output here.
    // Use libcore/cmd/boxoption to generate

    @KxsSerializable
    open class Options : SingBoxOption() {

        @JvmField
        var `$schema`: String? = null

        @JvmField
        var log: LogOptions? = null

        @JvmField
        var dns: DNSOptions? = null

        @JvmField
        var ntp: NTPOptions? = null

        @JvmField
        var certificate: CertificateOptions? = null

        @JvmField
        var endpoints: MutableList<Endpoint>? = null

        @JvmField
        var inbounds: MutableList<Inbound>? = null

        @JvmField
        var outbounds: MutableList<Outbound>? = null

        @JvmField
        var route: RouteOptions? = null

        @JvmField
        var services: MutableList<Service>? = null

        @JvmField
        var experimental: ExperimentalOptions? = null

    }

    @KxsSerializable
    open class LogOptions : SingBoxOption() {

        @JvmField
        var disabled: Boolean? = null

        @JvmField
        var level: String? = null

        @JvmField
        var output: String? = null

        @JvmField
        var timestamp: Boolean? = null

    }

    @KxsSerializable
    open class NTPOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var interval: String? = null

        @JvmField
        var write_to_system: Boolean? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

    }

    @KxsSerializable
    open class CertificateOptions : SingBoxOption() {

        @JvmField
        var store: String? = null

        @JvmField
        var certificate: MutableList<String>? = null

        @JvmField
        var certificate_path: MutableList<String>? = null

        @JvmField
        var certificate_directory_path: MutableList<String>? = null

    }

    @KxsSerializable
    open class DNSOptions : SingBoxOption() {

        // Generate note: nested type RawDNSOptions
        @JvmField
        var servers: MutableList<NewDNSServerOptions>? = null

        @JvmField
        var rules: MutableList<DNSRule>? = null

        @SerialName("final")
        @JvmField
        var final_: String? = null

        @JvmField
        var reverse_mapping: Boolean? = null

        // Generate note: nested type DNSClientOptions
        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var disable_expire: Boolean? = null

        @JvmField
        var independent_cache: Boolean? = null

        @JvmField
        var cache_capacity: Int? = null

        @JvmField
        var client_subnet: String? = null

    }

    @KxsSerializable
    open class DNSClientOptions : SingBoxOption() {

        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var disable_expire: Boolean? = null

        @JvmField
        var independent_cache: Boolean? = null

        @JvmField
        var cache_capacity: Int? = null

        @JvmField
        var client_subnet: String? = null

    }

    @KxsSerializable
    open class ExperimentalOptions : SingBoxOption() {

        @JvmField
        var cache_file: CacheFileOptions? = null

        @JvmField
        var clash_api: ClashAPIOptions? = null

        @JvmField
        var v2ray_api: V2RayAPIOptions? = null

        @JvmField
        var debug: DebugOptions? = null

    }

    @KxsSerializable
    open class CacheFileOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var path: String? = null

        @JvmField
        var cache_id: String? = null

        @JvmField
        var store_fakeip: Boolean? = null

        @JvmField
        var store_rdrc: Boolean? = null

        @JvmField
        var rdrc_timeout: String? = null

    }

    @KxsSerializable
    open class ClashAPIOptions : SingBoxOption() {

        @JvmField
        var external_controller: String? = null

        @JvmField
        var external_ui: String? = null

        @JvmField
        var external_ui_download_url: String? = null

        @JvmField
        var external_ui_download_detour: String? = null

        @JvmField
        var secret: String? = null

        @JvmField
        var default_mode: String? = null

        @JvmField
        var access_control_allow_origin: MutableList<String>? = null

        @JvmField
        var access_control_allow_private_network: Boolean? = null

        @JvmField
        var cache_file: String? = null

        @JvmField
        var cache_id: String? = null

        @JvmField
        var store_mode: Boolean? = null

        @JvmField
        var store_selected: Boolean? = null

        @JvmField
        var store_fakeip: Boolean? = null

    }

    @KxsSerializable
    open class V2RayAPIOptions : SingBoxOption() {

        @JvmField
        var listen: String? = null

        @JvmField
        var stats: V2RayStatsServiceOptions? = null

    }

    @KxsSerializable
    open class V2RayStatsServiceOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var inbounds: MutableList<String>? = null

        @JvmField
        var outbounds: MutableList<String>? = null

        @JvmField
        var users: MutableList<String>? = null

    }

    @KxsSerializable
    open class DebugOptions : SingBoxOption() {

        @JvmField
        var listen: String? = null

        @JvmField
        var gc_percent: Int? = null

        @JvmField
        var max_stack: Int? = null

        @JvmField
        var max_threads: Int? = null

        @JvmField
        var panic_on_fault: Boolean? = null

        @JvmField
        var trace_back: String? = null

        @JvmField
        var memory_limit: Int? = null

        @JvmField
        var oom_killer: Boolean? = null

    }

    @KxsSerializable
    open class RouteOptions : SingBoxOption() {

        @JvmField
        var geoip: JsonElement? = null

        @JvmField
        var geosite: JsonElement? = null

        @JvmField
        var rules: MutableList<Rule>? = null

        @JvmField
        var rule_set: MutableList<RuleSet>? = null

        @SerialName("final")
        @JvmField
        var final_: String? = null

        @JvmField
        var find_process: Boolean? = null

        @JvmField
        var auto_detect_interface: Boolean? = null

        @JvmField
        var override_android_vpn: Boolean? = null

        @JvmField
        var default_interface: String? = null

        @JvmField
        var default_mark: Int? = null

        @JvmField
        var default_domain_resolver: DomainResolveOptions? = null

        @JvmField
        var default_network_strategy: String? = null

        @JvmField
        var default_network_type: MutableList<String>? = null

        @JvmField
        var default_fallback_network_type: MutableList<String>? = null

        @JvmField
        var default_fallback_delay: String? = null

    }

    @KxsSerializable
    open class RuleSet : SingBoxOption() {

        @JvmField
        var type: String? = null

        @JvmField
        var tag: String? = null

        @JvmField
        var format: String? = null

    }

    @KxsSerializable
    open class HeadlessRule : SingBoxOption() {

        @JvmField
        var type: String? = null

    }

    @KxsSerializable
    open class UDPOverTCPOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var version: Int? = null

    }

    @KxsSerializable
    open class OutboundMultiplexOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var protocol: String? = null

        @JvmField
        var max_connections: Int? = null

        @JvmField
        var min_streams: Int? = null

        @JvmField
        var max_streams: Int? = null

        @JvmField
        var padding: Boolean? = null

        @JvmField
        var brutal: BrutalOptions? = null

    }

    @KxsSerializable
    open class BrutalOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var up_mbps: Int? = null

        @JvmField
        var down_mbps: Int? = null

    }

    @KxsSerializable
    open class OutboundTLSOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var disable_sni: Boolean? = null

        @JvmField
        var server_name: String? = null

        @JvmField
        var insecure: Boolean? = null

        @JvmField
        var alpn: MutableList<String>? = null

        @JvmField
        var min_version: String? = null

        @JvmField
        var max_version: String? = null

        @JvmField
        var cipher_suites: MutableList<String>? = null

        @JvmField
        var curve_preferences: MutableList<Int>? = null

        @JvmField
        var certificate: MutableList<String>? = null

        @JvmField
        var certificate_path: String? = null

        @JvmField
        var certificate_public_key_sha256: MutableList<String>? = null

        @JvmField
        var client_certificate: MutableList<String>? = null

        @JvmField
        var client_certificate_path: String? = null

        @JvmField
        var client_key: MutableList<String>? = null

        @JvmField
        var client_key_path: String? = null

        @JvmField
        var fragment: Boolean? = null

        @JvmField
        var fragment_fallback_delay: String? = null

        @JvmField
        var record_fragment: Boolean? = null

        @JvmField
        var kernel_tx: Boolean? = null

        @JvmField
        var kernel_rx: Boolean? = null

        @JvmField
        var ech: OutboundECHOptions? = null

        @JvmField
        var utls: OutboundUTLSOptions? = null

        @JvmField
        var reality: OutboundRealityOptions? = null

    }

    @KxsSerializable
    open class OutboundUTLSOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var fingerprint: String? = null

    }

    @KxsSerializable
    open class OutboundRealityOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var public_key: String? = null

        @JvmField
        var short_id: String? = null

    }

    @KxsSerializable
    open class OutboundECHOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var config: MutableList<String>? = null

        @JvmField
        var config_path: String? = null

        @JvmField
        var query_server_name: String? = null

        @JvmField
        var pq_signature_schemes_enabled: Boolean? = null

        @JvmField
        var dynamic_record_sizing_disabled: Boolean? = null

    }

    @KxsSerializable
    open class InboundTLSOptions : SingBoxOption() {

        @JvmField
        var enabled: Boolean? = null

        @JvmField
        var server_name: String? = null

        @JvmField
        var insecure: Boolean? = null

        @JvmField
        var alpn: MutableList<String>? = null

        @JvmField
        var min_version: String? = null

        @JvmField
        var max_version: String? = null

        @JvmField
        var cipher_suites: MutableList<String>? = null

        @JvmField
        var curve_preferences: MutableList<Int>? = null

        @JvmField
        var certificate: MutableList<String>? = null

        @JvmField
        var certificate_path: String? = null

        @JvmField
        var client_authentication: Int? = null

        @JvmField
        var client_certificate: MutableList<String>? = null

        @JvmField
        var client_certificate_path: MutableList<String>? = null

        @JvmField
        var client_certificate_public_key_sha256: MutableList<String>? = null

        @JvmField
        var key: MutableList<String>? = null

        @JvmField
        var key_path: String? = null

        @JvmField
        var kernel_tx: Boolean? = null

        @JvmField
        var kernel_rx: Boolean? = null

        @JvmField
        var acme: JsonElement? = null

        @JvmField
        var ech: JsonElement? = null

        @JvmField
        var reality: JsonElement? = null

    }

    @KxsSerializable
    open class Hysteria2Obfs : SingBoxOption() {

        @JvmField
        var type: String? = null

        @JvmField
        var password: String? = null

    }

    @KxsSerializable
    open class WireGuardPeer : SingBoxOption() {

        @JvmField
        var address: String? = null

        @JvmField
        var port: Int? = null

        @JvmField
        var public_key: String? = null

        @JvmField
        var pre_shared_key: String? = null

        @JvmField
        var allowed_ips: MutableList<String>? = null

        @JvmField
        var persistent_keepalive_interval: Int? = null

        @JvmField
        var reserved: String? = null

    }

    @KxsSerializable
    open class DomainResolveOptions : SingBoxOption() {

        @JvmField
        var server: String? = null

        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var rewrite_ttl: Int? = null

        @JvmField
        var client_subnet: String? = null

    }

    @KxsSerializable
    open class Rule_Default : Rule() {

        // Generate note: nested type RawDefaultRule
        @JvmField
        var inbound: MutableList<String>? = null

        @JvmField
        var ip_version: Int? = null

        @JvmField
        var network: MutableList<String>? = null

        @JvmField
        var auth_user: MutableList<String>? = null

        @JvmField
        var protocol: MutableList<String>? = null

        @JvmField
        var client: MutableList<String>? = null

        @JvmField
        var domain: MutableList<String>? = null

        @JvmField
        var domain_suffix: MutableList<String>? = null

        @JvmField
        var domain_keyword: MutableList<String>? = null

        @JvmField
        var domain_regex: MutableList<String>? = null

        @JvmField
        var geosite: MutableList<String>? = null

        @JvmField
        var source_geoip: MutableList<String>? = null

        @JvmField
        var geoip: MutableList<String>? = null

        @JvmField
        var source_ip_cidr: MutableList<String>? = null

        @JvmField
        var source_ip_is_private: Boolean? = null

        @JvmField
        var ip_cidr: MutableList<String>? = null

        @JvmField
        var ip_is_private: Boolean? = null

        @JvmField
        var source_port: MutableList<Int>? = null

        @JvmField
        var source_port_range: MutableList<String>? = null

        @JvmField
        var port: MutableList<Int>? = null

        @JvmField
        var port_range: MutableList<String>? = null

        @JvmField
        var process_name: MutableList<String>? = null

        @JvmField
        var process_path: MutableList<String>? = null

        @JvmField
        var process_path_regex: MutableList<String>? = null

        @JvmField
        var package_name: MutableList<String>? = null

        @JvmField
        var user: MutableList<String>? = null

        @JvmField
        var user_id: MutableList<Int>? = null

        @JvmField
        var clash_mode: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var network_is_expensive: Boolean? = null

        @JvmField
        var network_is_constrained: Boolean? = null

        @JvmField
        var wifi_ssid: MutableList<String>? = null

        @JvmField
        var wifi_bssid: MutableList<String>? = null

        @JvmField
        var interface_address: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var network_interface_address: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var default_interface_address: MutableList<String>? = null

        @JvmField
        var preferred_by: MutableList<String>? = null

        @JvmField
        var rule_set: MutableList<String>? = null

        @JvmField
        var rule_set_ip_cidr_match_source: Boolean? = null

        @JvmField
        var invert: Boolean? = null

        @JvmField
        var rule_set_ipcidr_match_source: Boolean? = null

        // Generate Note: Action
        @JvmField
        var action: String? = null

        @JvmField
        var outbound: String? = null

        // Generate note: nested type RawRouteOptionsActionOptions
        @JvmField
        var override_address: String? = null

        @JvmField
        var override_port: Int? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var fallback_delay: Int? = null

        @JvmField
        var udp_disable_domain_unmapping: Boolean? = null

        @JvmField
        var udp_connect: Boolean? = null

        @JvmField
        var udp_timeout: String? = null

        @JvmField
        var tls_fragment: Boolean? = null

        @JvmField
        var tls_fragment_fallback_delay: String? = null

        @JvmField
        var tls_record_fragment: Boolean? = null

        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var domain_strategy: String? = null

        @JvmField
        var method: String? = null

        @JvmField
        var no_drop: Boolean? = null

        @JvmField
        var sniffer: MutableList<String>? = null

        @JvmField
        var timeout: String? = null

        @JvmField
        var server: String? = null

        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var rewrite_ttl: Int? = null

        @JvmField
        var client_subnet: String? = null

    }

    @KxsSerializable
    open class Rule_Logical : Rule() {

        // Generate note: nested type RawLogicalRule
        @JvmField
        var mode: String? = null

        @JvmField
        var rules: MutableList<Rule>? = null

        @JvmField
        var invert: Boolean? = null

        // Generate Note: Action
        @JvmField
        var action: String? = null

        @JvmField
        var outbound: String? = null

        // Generate note: nested type RawRouteOptionsActionOptions
        @JvmField
        var override_address: String? = null

        @JvmField
        var override_port: Int? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var fallback_delay: Int? = null

        @JvmField
        var udp_disable_domain_unmapping: Boolean? = null

        @JvmField
        var udp_connect: Boolean? = null

        @JvmField
        var udp_timeout: String? = null

        @JvmField
        var tls_fragment: Boolean? = null

        @JvmField
        var tls_fragment_fallback_delay: String? = null

        @JvmField
        var tls_record_fragment: Boolean? = null

        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var domain_strategy: String? = null

        @JvmField
        var method: String? = null

        @JvmField
        var no_drop: Boolean? = null

        @JvmField
        var sniffer: MutableList<String>? = null

        @JvmField
        var timeout: String? = null

        @JvmField
        var server: String? = null

        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var rewrite_ttl: Int? = null

        @JvmField
        var client_subnet: String? = null

    }

    @KxsSerializable
    open class DNSRule_Default : DNSRule() {

        // Generate note: nested type RawDefaultDNSRule
        @JvmField
        var inbound: MutableList<String>? = null

        @JvmField
        var ip_version: Int? = null

        @JvmField
        var query_type: MutableList<String>? = null

        @JvmField
        var network: MutableList<String>? = null

        @JvmField
        var auth_user: MutableList<String>? = null

        @JvmField
        var protocol: MutableList<String>? = null

        @JvmField
        var domain: MutableList<String>? = null

        @JvmField
        var domain_suffix: MutableList<String>? = null

        @JvmField
        var domain_keyword: MutableList<String>? = null

        @JvmField
        var domain_regex: MutableList<String>? = null

        @JvmField
        var geosite: MutableList<String>? = null

        @JvmField
        var source_geoip: MutableList<String>? = null

        @JvmField
        var geoip: MutableList<String>? = null

        @JvmField
        var ip_cidr: MutableList<String>? = null

        @JvmField
        var ip_is_private: Boolean? = null

        @JvmField
        var ip_accept_any: Boolean? = null

        @JvmField
        var source_ip_cidr: MutableList<String>? = null

        @JvmField
        var source_ip_is_private: Boolean? = null

        @JvmField
        var source_port: MutableList<Int>? = null

        @JvmField
        var source_port_range: MutableList<String>? = null

        @JvmField
        var port: MutableList<Int>? = null

        @JvmField
        var port_range: MutableList<String>? = null

        @JvmField
        var process_name: MutableList<String>? = null

        @JvmField
        var process_path: MutableList<String>? = null

        @JvmField
        var process_path_regex: MutableList<String>? = null

        @JvmField
        var package_name: MutableList<String>? = null

        @JvmField
        var user: MutableList<String>? = null

        @JvmField
        var user_id: MutableList<Int>? = null

        @JvmField
        var outbound: MutableList<String>? = null

        @JvmField
        var clash_mode: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var network_is_expensive: Boolean? = null

        @JvmField
        var network_is_constrained: Boolean? = null

        @JvmField
        var wifi_ssid: MutableList<String>? = null

        @JvmField
        var wifi_bssid: MutableList<String>? = null

        @JvmField
        var interface_address: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var network_interface_address: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var default_interface_address: MutableList<String>? = null

        @JvmField
        var rule_set: MutableList<String>? = null

        @JvmField
        var rule_set_ip_cidr_match_source: Boolean? = null

        @JvmField
        var rule_set_ip_cidr_accept_empty: Boolean? = null

        @JvmField
        var invert: Boolean? = null

        @JvmField
        var rule_set_ipcidr_match_source: Boolean? = null

        // Generate Note: Action
        @JvmField
        var action: String? = null

        @JvmField
        var server: String? = null

        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var rewrite_ttl: Int? = null

        @JvmField
        var client_subnet: String? = null

        @JvmField
        var rcode: String? = null

        @JvmField
        var answer: MutableList<String>? = null

        @JvmField
        var ns: MutableList<String>? = null

        @JvmField
        var extra: MutableList<String>? = null

        @JvmField
        var method: String? = null

        @JvmField
        var no_drop: Boolean? = null

    }

    @KxsSerializable
    open class DNSRule_Logical : DNSRule() {

        // Generate note: nested type RawLogicalDNSRule
        @JvmField
        var mode: String? = null

        @JvmField
        var rules: MutableList<DNSRule>? = null

        @JvmField
        var invert: Boolean? = null

        // Generate Note: Action
        @JvmField
        var action: String? = null

        @JvmField
        var server: String? = null

        @JvmField
        var strategy: String? = null

        @JvmField
        var disable_cache: Boolean? = null

        @JvmField
        var rewrite_ttl: Int? = null

        @JvmField
        var client_subnet: String? = null

        @JvmField
        var rcode: String? = null

        @JvmField
        var answer: MutableList<String>? = null

        @JvmField
        var ns: MutableList<String>? = null

        @JvmField
        var extra: MutableList<String>? = null

        @JvmField
        var method: String? = null

        @JvmField
        var no_drop: Boolean? = null

    }

    @KxsSerializable
    open class RuleSet_Plain : RuleSet() {

        @JvmField
        var rules: MutableList<HeadlessRule>? = null

    }

    @KxsSerializable
    open class RuleSet_Local : RuleSet() {

        @JvmField
        var path: String? = null

    }

    @KxsSerializable
    open class RuleSet_Remote : RuleSet() {

        @JvmField
        var url: String? = null

        @JvmField
        var download_detour: String? = null

        @JvmField
        var update_interval: String? = null

    }

    @KxsSerializable
    open class V2RayTransportOptions_V2RayHTTPOptions : V2RayTransportOptions() {

        @JvmField
        var host: MutableList<String>? = null

        @JvmField
        var path: String? = null

        @JvmField
        var method: String? = null

        @JvmField
        var headers: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var idle_timeout: String? = null

        @JvmField
        var ping_timeout: String? = null

    }

    @KxsSerializable
    open class V2RayTransportOptions_V2RayWebsocketOptions : V2RayTransportOptions() {

        @JvmField
        var path: String? = null

        @JvmField
        var headers: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var max_early_data: Int? = null

        @JvmField
        var early_data_header_name: String? = null

    }

    @KxsSerializable
    open class V2RayTransportOptions_V2RayQUICOptions : V2RayTransportOptions() {

    }

    @KxsSerializable
    open class V2RayTransportOptions_V2RayGRPCOptions : V2RayTransportOptions() {

        @JvmField
        var service_name: String? = null

        @JvmField
        var idle_timeout: String? = null

        @JvmField
        var ping_timeout: String? = null

        @JvmField
        var permit_without_stream: Boolean? = null

    }

    @KxsSerializable
    open class V2RayTransportOptions_V2RayHTTPUpgradeOptions : V2RayTransportOptions() {

        @JvmField
        var host: String? = null

        @JvmField
        var path: String? = null

        @JvmField
        var headers: MutableMap<String, MutableList<String>>? = null

    }

    @KxsSerializable
    open class Inbound_HTTPMixedOptions : Inbound() {

        // Generate note: nested type ListenOptions
        @JvmField
        var listen: String? = null

        @JvmField
        var listen_port: Int? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var udp_timeout: Long? = null

        @JvmField
        var proxy_protocol: Boolean? = null

        @JvmField
        var proxy_protocol_accept_no_header: Boolean? = null

        // Generate note: nested type InboundOptions
        @JvmField
        var sniff: Boolean? = null

        @JvmField
        var sniff_override_destination: Boolean? = null

        @JvmField
        var sniff_timeout: String? = null

        @JvmField
        var domain_strategy: String? = null

        @JvmField
        var udp_disable_domain_unmapping: Boolean? = null

        @JvmField
        var detour: String? = null

        @JvmField
        var users: MutableList<User>? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var set_system_proxy: Boolean? = null

        // Generate note: nested type InboundTLSOptionsContainer
        @JvmField
        var tls: InboundTLSOptions? = null

    }

    @KxsSerializable
    open class Inbound_TunOptions : Inbound() {

        @JvmField
        var interface_name: String? = null

        @JvmField
        var mtu: Int? = null

        @JvmField
        var address: MutableList<String>? = null

        @JvmField
        var auto_route: Boolean? = null

        @JvmField
        var iproute2_table_index: Int? = null

        @JvmField
        var iproute2_rule_index: Int? = null

        @JvmField
        var auto_redirect: Boolean? = null

        @JvmField
        var auto_redirect_input_mark: Int? = null

        @JvmField
        var auto_redirect_output_mark: Int? = null

        @JvmField
        var auto_redirect_reset_mark: Int? = null

        @JvmField
        var auto_redirect_nfqueue: Int? = null

        @JvmField
        var auto_redirect_iproute2_fallback_rule_index: Int? = null

        @JvmField
        var exclude_mptcp: Boolean? = null

        @JvmField
        var loopback_address: MutableList<String>? = null

        @JvmField
        var strict_route: Boolean? = null

        @JvmField
        var route_address: MutableList<String>? = null

        @JvmField
        var route_address_set: MutableList<String>? = null

        @JvmField
        var route_exclude_address: MutableList<String>? = null

        @JvmField
        var route_exclude_address_set: MutableList<String>? = null

        @JvmField
        var include_interface: MutableList<String>? = null

        @JvmField
        var exclude_interface: MutableList<String>? = null

        @JvmField
        var include_uid: MutableList<Int>? = null

        @JvmField
        var include_uid_range: MutableList<String>? = null

        @JvmField
        var exclude_uid: MutableList<Int>? = null

        @JvmField
        var exclude_uid_range: MutableList<String>? = null

        @JvmField
        var include_android_user: MutableList<Int>? = null

        @JvmField
        var include_package: MutableList<String>? = null

        @JvmField
        var exclude_package: MutableList<String>? = null

        @JvmField
        var udp_timeout: Long? = null

        @JvmField
        var stack: String? = null

        @JvmField
        var platform: Inbound_TunPlatformOptions? = null

        // Generate note: nested type InboundOptions
        @JvmField
        var sniff: Boolean? = null

        @JvmField
        var sniff_override_destination: Boolean? = null

        @JvmField
        var sniff_timeout: String? = null

        @JvmField
        var domain_strategy: String? = null

        @JvmField
        var udp_disable_domain_unmapping: Boolean? = null

        @JvmField
        var detour: String? = null

        @JvmField
        var gso: Boolean? = null

        @JvmField
        var inet4_address: MutableList<String>? = null

        @JvmField
        var inet6_address: MutableList<String>? = null

        @JvmField
        var inet4_route_address: MutableList<String>? = null

        @JvmField
        var inet6_route_address: MutableList<String>? = null

        @JvmField
        var inet4_route_exclude_address: MutableList<String>? = null

        @JvmField
        var inet6_route_exclude_address: MutableList<String>? = null

        @JvmField
        var endpoint_independent_nat: Boolean? = null

    }

    @KxsSerializable
    open class Inbound_TunPlatformOptions : Inbound() {

        @JvmField
        var http_proxy: Inbound_HTTPProxyOptions? = null

    }

    @KxsSerializable
    open class Inbound_HTTPProxyOptions : Inbound() {

        @JvmField
        var enabled: Boolean? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var bypass_domain: MutableList<String>? = null

        @JvmField
        var match_domain: MutableList<String>? = null

    }

    @KxsSerializable
    open class Inbound_DirectOptions : Inbound() {

        // Generate note: nested type ListenOptions
        @JvmField
        var listen: String? = null

        @JvmField
        var listen_port: Int? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var udp_timeout: Long? = null

        @JvmField
        var proxy_protocol: Boolean? = null

        @JvmField
        var proxy_protocol_accept_no_header: Boolean? = null

        // Generate note: nested type InboundOptions
        @JvmField
        var sniff: Boolean? = null

        @JvmField
        var sniff_override_destination: Boolean? = null

        @JvmField
        var sniff_timeout: String? = null

        @JvmField
        var domain_strategy: String? = null

        @JvmField
        var udp_disable_domain_unmapping: Boolean? = null

        @JvmField
        var detour: String? = null

        @JvmField
        var network: String? = null

        @JvmField
        var override_address: String? = null

        @JvmField
        var override_port: Int? = null

    }

    @KxsSerializable
    open class Outbound_DirectOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        @JvmField
        var override_address: String? = null

        @JvmField
        var override_port: Int? = null

        @JvmField
        var proxy_protocol: Int? = null

    }

    @KxsSerializable
    open class Outbound_ShadowsocksOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var method: String? = null

        @JvmField
        var password: String? = null

        @JvmField
        var plugin: String? = null

        @JvmField
        var plugin_opts: String? = null

        @JvmField
        var network: String? = null

        @JvmField
        var udp_over_tcp: UDPOverTCPOptions? = null

        @JvmField
        var multiplex: OutboundMultiplexOptions? = null

    }

    @KxsSerializable
    open class Outbound_ShadowTLSOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var version: Int? = null

        @JvmField
        var password: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

    }

    @KxsSerializable
    open class Outbound_SelectorOptions : Outbound() {

        @JvmField
        var outbounds: MutableList<String>? = null

        @SerialName("default")
        @JvmField
        var default_: String? = null

        @JvmField
        var interrupt_exist_connections: Boolean? = null

    }

    @KxsSerializable
    open class Outbound_URLTestOptions : Outbound() {

        @JvmField
        var outbounds: MutableList<String>? = null

        @JvmField
        var url: String? = null

        @JvmField
        var interval: String? = null

        @JvmField
        var tolerance: Int? = null

        @JvmField
        var idle_timeout: String? = null

        @JvmField
        var interrupt_exist_connections: Boolean? = null

    }

    @KxsSerializable
    open class Outbound_SOCKSOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var version: String? = null

        @JvmField
        var username: String? = null

        @JvmField
        var password: String? = null

        @JvmField
        var network: String? = null

        @JvmField
        var udp_over_tcp: UDPOverTCPOptions? = null

    }

    @KxsSerializable
    open class Outbound_HTTPOptions : Outbound() {

        // Generate note: nested type HTTPOutboundOptions
        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var username: String? = null

        @JvmField
        var password: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var path: String? = null

        @JvmField
        var headers: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var udp_over_tcp: UDPOverTCPOptions? = null

    }

    @KxsSerializable
    open class Outbound_SSHOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var user: String? = null

        @JvmField
        var password: String? = null

        @JvmField
        var private_key: MutableList<String>? = null

        @JvmField
        var private_key_path: String? = null

        @JvmField
        var private_key_passphrase: String? = null

        @JvmField
        var host_key: MutableList<String>? = null

        @JvmField
        var host_key_algorithms: MutableList<String>? = null

        @JvmField
        var client_version: String? = null

    }

    @KxsSerializable
    open class Outbound_TrojanOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var password: String? = null

        @JvmField
        var network: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var multiplex: OutboundMultiplexOptions? = null

        @JvmField
        var transport: V2RayTransportOptions? = null

    }

    @KxsSerializable
    open class Outbound_HysteriaOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var server_ports: MutableList<String>? = null

        @JvmField
        var hop_interval: String? = null

        @JvmField
        var up: String? = null

        @JvmField
        var up_mbps: Int? = null

        @JvmField
        var down: String? = null

        @JvmField
        var down_mbps: Int? = null

        @JvmField
        var obfs: String? = null

        @JvmField
        var auth: String? = null

        @JvmField
        var auth_str: String? = null

        @JvmField
        var recv_window_conn: Long? = null

        @JvmField
        var recv_window: Long? = null

        @JvmField
        var disable_mtu_discovery: Boolean? = null

        @JvmField
        var network: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

    }

    @KxsSerializable
    open class Outbound_Hysteria2Options : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var server_ports: MutableList<String>? = null

        @JvmField
        var hop_interval: String? = null

        @JvmField
        var up_mbps: Int? = null

        @JvmField
        var down_mbps: Int? = null

        @JvmField
        var obfs: Hysteria2Obfs? = null

        @JvmField
        var password: String? = null

        @JvmField
        var network: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var brutal_debug: Boolean? = null

    }

    @KxsSerializable
    open class Outbound_TUICOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var uuid: String? = null

        @JvmField
        var password: String? = null

        @JvmField
        var congestion_control: String? = null

        @JvmField
        var udp_relay_mode: String? = null

        @JvmField
        var udp_over_stream: Boolean? = null

        @JvmField
        var zero_rtt_handshake: Boolean? = null

        @JvmField
        var heartbeat: String? = null

        @JvmField
        var network: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

    }

    @KxsSerializable
    open class Outbound_VLESSOptions : Outbound() {

        // Generate note: nested type VLESSOutboundOptions
        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var uuid: String? = null

        @JvmField
        var flow: String? = null

        @JvmField
        var network: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var multiplex: OutboundMultiplexOptions? = null

        @JvmField
        var transport: V2RayTransportOptions? = null

        @JvmField
        var packet_encoding: String? = null

        @JvmField
        var encryption: String? = null

    }

    @KxsSerializable
    open class Outbound_VMessOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var uuid: String? = null

        @JvmField
        var security: String? = null

        @JvmField
        var alter_id: Int? = null

        @JvmField
        var global_padding: Boolean? = null

        @JvmField
        var authenticated_length: Boolean? = null

        @JvmField
        var network: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var packet_encoding: String? = null

        @JvmField
        var multiplex: OutboundMultiplexOptions? = null

        @JvmField
        var transport: V2RayTransportOptions? = null

    }

    @KxsSerializable
    open class Outbound_AnyTLSOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var password: String? = null

        @JvmField
        var idle_session_check_interval: String? = null

        @JvmField
        var idle_session_timeout: String? = null

        @JvmField
        var min_idle_session: Int? = null

    }

    @KxsSerializable
    open class Outbound_JuicityOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var uuid: String? = null

        @JvmField
        var password: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var pin_cert_sha256: String? = null

    }

    @KxsSerializable
    open class Outbound_NaiveOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var username: String? = null

        @JvmField
        var password: String? = null

        @JvmField
        var insecure_concurrency: Int? = null

        @JvmField
        var extra_headers: MutableMap<String, MutableList<String>>? = null

        @JvmField
        var udp_over_tcp: UDPOverTCPOptions? = null

        @JvmField
        var quic: Boolean? = null

        @JvmField
        var quic_congestion_control: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

    }

    @KxsSerializable
    open class Outbound_TrustTunnelOptions : Outbound() {

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type ServerOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var username: String? = null

        @JvmField
        var password: String? = null

        @JvmField
        var health_check: Boolean? = null

        @JvmField
        var quic: Boolean? = null

        @JvmField
        var quic_congestion_control: String? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

    }

    @KxsSerializable
    open class Endpoint_WireGuardOptions : Endpoint() {

        @JvmField
        var system: Boolean? = null

        @JvmField
        var name: String? = null

        @JvmField
        var mtu: Int? = null

        @JvmField
        var address: MutableList<String>? = null

        @JvmField
        var private_key: String? = null

        @JvmField
        var listen_port: Int? = null

        @JvmField
        var peers: MutableList<WireGuardPeer>? = null

        @JvmField
        var udp_timeout: String? = null

        @JvmField
        var workers: Int? = null

        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

    }

    @KxsSerializable
    open class NewDNSServerOptions_HostsDNSServerOptions : NewDNSServerOptions() {

        @JvmField
        var path: MutableList<String>? = null

        @JvmField
        var predefined: MutableMap<String, MutableList<String>>? = null

    }

    @KxsSerializable
    open class NewDNSServerOptions_LocalDNSServerOptions : NewDNSServerOptions() {

        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        @JvmField
        var prefer_go: Boolean? = null

    }

    @KxsSerializable
    open class NewDNSServerOptions_RemoteDNSServerOptions : NewDNSServerOptions() {

        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type DNSServerAddressOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

    }

    @KxsSerializable
    open class NewDNSServerOptions_RemoteTCPDNSServerOptions : NewDNSServerOptions() {

        // Generate note: nested type RemoteDNSServerOptions
        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type DNSServerAddressOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        @JvmField
        var reuse: Boolean? = null

        @JvmField
        var pipeline: Boolean? = null

        @JvmField
        var max_queries: Int? = null

    }

    @KxsSerializable
    open class NewDNSServerOptions_RemoteTLSDNSServerOptions : NewDNSServerOptions() {

        // Generate note: nested type RemoteDNSServerOptions
        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type DNSServerAddressOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var pipeline: Boolean? = null

        @JvmField
        var max_queries: Int? = null

    }

    @KxsSerializable
    open class NewDNSServerOptions_RemoteHTTPSDNSServerOptions : NewDNSServerOptions() {

        // Generate note: nested type RemoteTLSDNSServerOptions
        // Generate note: nested type RemoteDNSServerOptions
        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        @JvmField
        var detour: String? = null

        @JvmField
        var bind_interface: String? = null

        @JvmField
        var inet4_bind_address: String? = null

        @JvmField
        var inet6_bind_address: String? = null

        @JvmField
        var bind_address_no_port: Boolean? = null

        @JvmField
        var protect_path: String? = null

        @JvmField
        var routing_mark: Int? = null

        @JvmField
        var reuse_addr: Boolean? = null

        @JvmField
        var netns: String? = null

        @JvmField
        var connect_timeout: String? = null

        @JvmField
        var tcp_fast_open: Boolean? = null

        @JvmField
        var tcp_multi_path: Boolean? = null

        @JvmField
        var disable_tcp_keep_alive: Boolean? = null

        @JvmField
        var tcp_keep_alive: String? = null

        @JvmField
        var tcp_keep_alive_interval: String? = null

        @JvmField
        var udp_fragment: Boolean? = null

        @JvmField
        var domain_resolver: DomainResolveOptions? = null

        @JvmField
        var network_strategy: String? = null

        @JvmField
        var network_type: MutableList<String>? = null

        @JvmField
        var fallback_network_type: MutableList<String>? = null

        @JvmField
        var fallback_delay: String? = null

        @JvmField
        var domain_strategy: String? = null

        // Generate note: nested type DNSServerAddressOptions
        @JvmField
        var server: String? = null

        @JvmField
        var server_port: Int? = null

        // Generate note: nested type OutboundTLSOptionsContainer
        @JvmField
        var tls: OutboundTLSOptions? = null

        @JvmField
        var path: String? = null

        @JvmField
        var method: String? = null

        @JvmField
        var headers: MutableMap<String, MutableList<String>>? = null

    }

    @KxsSerializable
    open class NewDNSServerOptions_FakeIPDNSServerOptions : NewDNSServerOptions() {

        @JvmField
        var inet4_range: String? = null

        @JvmField
        var inet6_range: String? = null

    }


}
