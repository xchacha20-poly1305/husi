package io.nekohasekai.sagernet.fmt;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class SingBoxOptions {

    public static final String NetworkTCP = "tcp";
    public static final String NetworkUDP = "udp";
    public static final String NetworkICMP = "icmp";

    public static final String RULE_SET_FORMAT_BINARY = "binary";
    public static final String RULE_SET_TYPE_REMOTE = "remote";
    public static final String RULE_SET_TYPE_LOCAL = "local";

    public static final String TYPE_TUN = "tun";
    public static final String TYPE_SELECTOR = "selector";
    public static final String TYPE_URLTEST = "urltest";
    public static final String TYPE_MIXED = "mixed";
    public static final String TYPE_DIRECT = "direct";
    public static final String TYPE_BLOCK = "block";
    public static final String TYPE_HTTP = "http";
    public static final String TYPE_HYSTERIA = "hysteria";
    public static final String TYPE_HYSTERIA2 = "hysteria2";
    public static final String TYPE_SHADOWSOCKS = "shadowsocks";
    public static final String TYPE_SOCKS = "socks";
    public static final String TYPE_SSH = "ssh";
    public static final String TYPE_TROJAN = "trojan";
    public static final String TYPE_TUIC = "tuic";
    public static final String TYPE_VMESS = "vmess";
    public static final String TYPE_VLESS = "vless";
    public static final String TYPE_WIREGUARD = "wireguard";
    public static final String TYPE_SHADOWTLS = "shadowtls";
    public static final String TYPE_ANYTLS = "anytls";
    public static final String TYPE_NAIVE = "naive";

    public static final String TRANSPORT_WS = "ws";
    public static final String TRANSPORT_HTTPUPGRADE = "httpupgrade";
    public static final String TRANSPORT_HTTP = "http";
    public static final String TRANSPORT_QUIC = "quic";
    public static final String TRANSPORT_GRPC = "grpc";

    public static final String TYPE_LOGICAL = "logical";

    public static final String ACTION_ROUTE = "route";
    public static final String ACTION_ROUTE_OPTIONS = "route-options";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_HIJACK_DNS = "hijack-dns";
    public static final String ACTION_SNIFF = "sniff";
    public static final String ACTION_RESOLVE = "resolve";

    public static final String LOGICAL_OR = "or";
    public static final String LOGICAL_AND = "and";

    public static final String SNIFF_HTTP = "http";
    public static final String SNIFF_TLS = "tls";
    public static final String SNIFF_QUIC = "quic";
    public static final String SNIFF_STUN = "stun";
    public static final String SNIFF_DNS = "dns";
    public static final String SNIFF_BITTORRENT = "bittorrent";
    public static final String SNIFF_DTLS = "dtls";
    public static final String SNIFF_SSH = "ssh";
    public static final String SNIFF_RDP = "rdp";
    public static final String SNIFF_NTP = "ntp";

    public static final String STRATEGY_PREFER_IPV6 = "prefer_ipv6";
    public static final String STRATEGY_PREFER_IPV4 = "prefer_ipv4";
    public static final String STRATEGY_IPV4_ONLY = "ipv4_only";
    public static final String STRATEGY_IPV6_ONLY = "ipv6_only";

    public static final String STRATEGY_DEFAULT = "default";
    public static final String STRATEGY_HYBRID = "hybrid";
    public static final String STRATEGY_FALLBACK = "fallback";

    // Custom for URL, not belongs to box.
    public static final String DNS_TYPE_LOCAL = "local";
    public static final String DNS_TYPE_UDP = "udp";
    public static final String DNS_TYPE_TCP = "tcp";
    public static final String DNS_TYPE_TLS = "tls";
    public static final String DNS_TYPE_HTTPS = "https";
    public static final String DNS_TYPE_QUIC = "quic";
    public static final String DNS_TYPE_H3 = "h3";
    public static final String DNS_TYPE_PREDEFINED = "predefined";
    public static final String DNS_TYPE_RCODE = "rcode";
    public static final String DNS_TYPE_FAKEIP = "fakeip";
    public static final String DNS_TYPE_HOSTS = "hosts";

    public static final String NETWORK_TYPE_WIFI = "wifi";
    public static final String NETWORK_TYPE_CELLULAR = "cellular";
    public static final String NETWORK_TYPE_ETHERNET = "ethernet";
    public static final String NETWORK_TYPE_OTHER = "other";

    public static final String FINGERPRINT_CHROME = "chrome";
    public static final String FINGERPRINT_FIREFOX = "firefox";
    public static final String FINGERPRINT_EDGE = "edge";
    public static final String FINGERPRINT_SAFARI = "safari";
    public static final String FINGERPRINT_360 = "360";
    public static final String FINGERPRINT_QQ = "qq";
    public static final String FINGERPRINT_IOS = "ios";
    public static final String FINGERPRINT_ANDROID = "android";
    public static final String FINGERPRINT_RANDOM = "random";
    public static final String FINGERPRINT_RANDOMIZED = "randomized";

    // base

    public static class SingBoxOption {
    }

    // custom classes

    public static class User {
        public String username;
        public String password;
    }

    public static class MyOptions extends SingBoxOption {
        public LogOptions log;

        public MyDNSOptions dns;

        public NTPOptions ntp;

        public List<Inbound> inbounds;

        public List<Map<String, Object>> outbounds;

        public List<Map<String, Object>> endpoints;

        public MyRouteOptions route;

        public ExperimentalOptions experimental;

        // public MITMOptions mitm;

        // public List<Script> scripts;
    }

    public static class MyDNSOptions extends SingBoxOption {

        // Generate note: nested type RawDNSOptions
        public List<NewDNSServerOptions> servers;

        public List<Map<String, Object>> rules;

        @SerializedName("final")
        public String final_;

        public Boolean reverse_mapping;

        // Generate note: nested type DNSClientOptions
        public String strategy;

        public Boolean disable_cache;

        public Boolean disable_expire;

        public Boolean independent_cache;

        public Integer cache_capacity;

        public String client_subnet;

    }

    public static class MyRouteOptions extends SingBoxOption {

        public List<Map<String, Object>> rules;

        public List<RuleSet> rule_set;

        @SerializedName("final")
        public String final_;

        public Boolean find_process;

        public Boolean auto_detect_interface;

        public Boolean override_android_vpn;

        public String default_interface;

        public Integer default_mark;

        public DomainResolveOptions default_domain_resolver;

        public String default_network_strategy;

        public List<String> default_network_type;

        public List<String> default_fallback_network_type;

        public String default_fallback_delay;

    }

    // Classes have optional field
    // Generated in line + 163

    public static class Inbound extends SingBoxOption {

        public String type;

        public String tag;

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

    public static class Outbound extends SingBoxOption {

        public String type;

        public String tag;

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

    public static class Endpoint extends Outbound {

        // Generate note: option type:  public WireGuardEndpointOptions WireGuardEndpointOptions;

    }

    public static class Rule extends SingBoxOption {

        public String type;

        // Generate note: option type:  public DefaultRule DefaultOptions;

        // Generate note: option type:  public LogicalRule LogicalOptions;

    }

    public static class DNSRule extends SingBoxOption {

        public String type;

        // Generate note: option type:  public DefaultDNSRule DefaultOptions;

        // Generate note: option type:  public LogicalDNSRule LogicalOptions;

    }

    public static class V2RayTransportOptions extends SingBoxOption {

        public String type;

        // Generate note: option type:  public V2RayHTTPOptions HTTPOptions;

        // Generate note: option type:  public V2RayWebsocketOptions WebsocketOptions;

        // Generate note: option type:  public V2RayQUICOptions QUICOptions;

        // Generate note: option type:  public V2RayGRPCOptions GRPCOptions;

        // Generate note: option type:  public V2RayHTTPUpgradeOptions HTTPUpgradeOptions;
    }


    public static class NewDNSServerOptions extends SingBoxOption {

        public String type;

        public String tag;

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

    public static class Service extends SingBoxOption {

        public String type;

        public String tag;

    }

    // Paste generate output here.
    // Use libcore/cmd/boxoption to generate

    public static class Options extends SingBoxOption {

        public String $schema;

        public LogOptions log;

        public DNSOptions dns;

        public NTPOptions ntp;

        public CertificateOptions certificate;

        public List<Endpoint> endpoints;

        public List<Inbound> inbounds;

        public List<Outbound> outbounds;

        public RouteOptions route;

        public List<Service> services;

        public ExperimentalOptions experimental;

    }

    public static class LogOptions extends SingBoxOption {

        public Boolean disabled;

        public String level;

        public String output;

        public Boolean timestamp;

    }

    public static class NTPOptions extends SingBoxOption {

        public Boolean enabled;

        public String interval;

        public Boolean write_to_system;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

    }

    public static class CertificateOptions extends SingBoxOption {

        public String store;

        public List<String> certificate;

        public List<String> certificate_path;

        public List<String> certificate_directory_path;

    }

    public static class DNSOptions extends SingBoxOption {

        // Generate note: nested type RawDNSOptions
        public List<NewDNSServerOptions> servers;

        public List<DNSRule> rules;

        @SerializedName("final")
        public String final_;

        public Boolean reverse_mapping;

        // Generate note: nested type DNSClientOptions
        public String strategy;

        public Boolean disable_cache;

        public Boolean disable_expire;

        public Boolean independent_cache;

        public Integer cache_capacity;

        public String client_subnet;

    }

    public static class DNSClientOptions extends SingBoxOption {

        public String strategy;

        public Boolean disable_cache;

        public Boolean disable_expire;

        public Boolean independent_cache;

        public Integer cache_capacity;

        public String client_subnet;

    }

    public static class ExperimentalOptions extends SingBoxOption {

        public CacheFileOptions cache_file;

        public ClashAPIOptions clash_api;

        public V2RayAPIOptions v2ray_api;

        public DebugOptions debug;

    }

    public static class CacheFileOptions extends SingBoxOption {

        public Boolean enabled;

        public String path;

        public String cache_id;

        public Boolean store_fakeip;

        public Boolean store_rdrc;

        public String rdrc_timeout;

    }

    public static class ClashAPIOptions extends SingBoxOption {

        public String external_controller;

        public String external_ui;

        public String external_ui_download_url;

        public String external_ui_download_detour;

        public String secret;

        public String default_mode;

        public List<String> access_control_allow_origin;

        public Boolean access_control_allow_private_network;

        public String cache_file;

        public String cache_id;

        public Boolean store_mode;

        public Boolean store_selected;

        public Boolean store_fakeip;

    }

    public static class V2RayAPIOptions extends SingBoxOption {

        public String listen;

        public V2RayStatsServiceOptions stats;

    }

    public static class V2RayStatsServiceOptions extends SingBoxOption {

        public Boolean enabled;

        public List<String> inbounds;

        public List<String> outbounds;

        public List<String> users;

    }

    public static class DebugOptions extends SingBoxOption {

        public String listen;

        public Integer gc_percent;

        public Integer max_stack;

        public Integer max_threads;

        public Boolean panic_on_fault;

        public String trace_back;

        public Integer memory_limit;

        public Boolean oom_killer;

    }

    public static class RouteOptions extends SingBoxOption {

        // public GeoIPOptions geoip;

        // public GeositeOptions geosite;

        public List<Rule> rules;

        public List<RuleSet> rule_set;

        @SerializedName("final")
        public String final_;

        public Boolean find_process;

        public Boolean auto_detect_interface;

        public Boolean override_android_vpn;

        public String default_interface;

        public Integer default_mark;

        public DomainResolveOptions default_domain_resolver;

        public String default_network_strategy;

        public List<String> default_network_type;

        public List<String> default_fallback_network_type;

        public String default_fallback_delay;

    }

    public static class RuleSet extends SingBoxOption {

        public String type;

        public String tag;

        public String format;

    }

    public static class HeadlessRule extends SingBoxOption {

        public String type;

    }

    public static class UDPOverTCPOptions extends SingBoxOption {

        public Boolean enabled;

        public Integer version;

    }

    public static class OutboundMultiplexOptions extends SingBoxOption {

        public Boolean enabled;

        public String protocol;

        public Integer max_connections;

        public Integer min_streams;

        public Integer max_streams;

        public Boolean padding;

        public BrutalOptions brutal;

    }

    public static class BrutalOptions extends SingBoxOption {

        public Boolean enabled;

        public Integer up_mbps;

        public Integer down_mbps;

    }

    public static class OutboundTLSOptions extends SingBoxOption {

        public Boolean enabled;

        public Boolean disable_sni;

        public String server_name;

        public Boolean insecure;

        public List<String> alpn;

        public String min_version;

        public String max_version;

        public List<String> cipher_suites;

        public List<Integer> curve_preferences;

        public List<String> certificate;

        public String certificate_path;

        public List<String> certificate_public_key_sha256;

        public List<String> client_certificate;

        public String client_certificate_path;

        public List<String> client_key;

        public String client_key_path;

        public Boolean fragment;

        public String fragment_fallback_delay;

        public Boolean record_fragment;

        public Boolean kernel_tx;

        public Boolean kernel_rx;

        public OutboundECHOptions ech;

        public OutboundUTLSOptions utls;

        public OutboundRealityOptions reality;

    }

    public static class OutboundUTLSOptions extends SingBoxOption {

        public Boolean enabled;

        public String fingerprint;

    }

    public static class OutboundRealityOptions extends SingBoxOption {

        public Boolean enabled;

        public String public_key;

        public String short_id;

    }

    public static class OutboundECHOptions extends SingBoxOption {

        public Boolean enabled;

        public List<String> config;

        public String config_path;

        public String query_server_name;

        public Boolean pq_signature_schemes_enabled;

        public Boolean dynamic_record_sizing_disabled;

    }

    public static class InboundTLSOptions extends SingBoxOption {

        public Boolean enabled;

        public String server_name;

        public Boolean insecure;

        public List<String> alpn;

        public String min_version;

        public String max_version;

        public List<String> cipher_suites;

        public List<Integer> curve_preferences;

        public List<String> certificate;

        public String certificate_path;

        public Integer client_authentication;

        public List<String> client_certificate;

        public List<String> client_certificate_path;

        public List<String> client_certificate_public_key_sha256;

        public List<String> key;

        public String key_path;

        public Boolean kernel_tx;

        public Boolean kernel_rx;

        // public InboundECHOptions ech;

        // public InboundECHOptions ech;

        // public InboundRealityOptions reality;

    }

    public static class Hysteria2Obfs extends SingBoxOption {

        public String type;

        public String password;

    }

    public static class WireGuardPeer extends SingBoxOption {

        public String address;

        public Integer port;

        public String public_key;

        public String pre_shared_key;

        public List<String> allowed_ips;

        public Integer persistent_keepalive_interval;

        public String reserved;

    }

    public static class DomainResolveOptions extends SingBoxOption {

        public String server;

        public String strategy;

        public Boolean disable_cache;

        public Integer rewrite_ttl;

        public String client_subnet;

    }

    public static class Rule_Default extends Rule {

        // Generate note: nested type RawDefaultRule
        public List<String> inbound;

        public Integer ip_version;

        public List<String> network;

        public List<String> auth_user;

        public List<String> protocol;

        public List<String> client;

        public List<String> domain;

        public List<String> domain_suffix;

        public List<String> domain_keyword;

        public List<String> domain_regex;

        public List<String> geosite;

        public List<String> source_geoip;

        public List<String> geoip;

        public List<String> source_ip_cidr;

        public Boolean source_ip_is_private;

        public List<String> ip_cidr;

        public Boolean ip_is_private;

        public List<Integer> source_port;

        public List<String> source_port_range;

        public List<Integer> port;

        public List<String> port_range;

        public List<String> process_name;

        public List<String> process_path;

        public List<String> process_path_regex;

        public List<String> package_name;

        public List<String> user;

        public List<Integer> user_id;

        public String clash_mode;

        public List<String> network_type;

        public Boolean network_is_expensive;

        public Boolean network_is_constrained;

        public List<String> wifi_ssid;

        public List<String> wifi_bssid;

        public Map<String, List<String>> interface_address;

        public Map<String, List<String>> network_interface_address;

        public List<String> default_interface_address;

        public List<String> preferred_by;

        public List<String> rule_set;

        public Boolean rule_set_ip_cidr_match_source;

        public Boolean invert;

        public Boolean rule_set_ipcidr_match_source;

        // Generate Note: Action
        public String action;

        public String outbound;

        // Generate note: nested type RawRouteOptionsActionOptions
        public String override_address;

        public Integer override_port;

        public String network_strategy;

        public Integer fallback_delay;

        public Boolean udp_disable_domain_unmapping;

        public Boolean udp_connect;

        public String udp_timeout;

        public Boolean tls_fragment;

        public String tls_fragment_fallback_delay;

        public Boolean tls_record_fragment;

        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public List<String> fallback_network_type;

        public String domain_strategy;

        public String method;

        public Boolean no_drop;

        public List<String> sniffer;

        public String timeout;

        public String server;

        public String strategy;

        public Boolean disable_cache;

        public Integer rewrite_ttl;

        public String client_subnet;

    }

    public static class Rule_Logical extends Rule {

        // Generate note: nested type RawLogicalRule
        public String mode;

        public List<Rule> rules;

        public Boolean invert;

        // Generate Note: Action
        public String action;

        public String outbound;

        // Generate note: nested type RawRouteOptionsActionOptions
        public String override_address;

        public Integer override_port;

        public String network_strategy;

        public Integer fallback_delay;

        public Boolean udp_disable_domain_unmapping;

        public Boolean udp_connect;

        public String udp_timeout;

        public Boolean tls_fragment;

        public String tls_fragment_fallback_delay;

        public Boolean tls_record_fragment;

        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String domain_strategy;

        public String method;

        public Boolean no_drop;

        public List<String> sniffer;

        public String timeout;

        public String server;

        public String strategy;

        public Boolean disable_cache;

        public Integer rewrite_ttl;

        public String client_subnet;

    }

    public static class DNSRule_Default extends DNSRule {

        // Generate note: nested type RawDefaultDNSRule
        public List<String> inbound;

        public Integer ip_version;

        public List<String> query_type;

        public List<String> network;

        public List<String> auth_user;

        public List<String> protocol;

        public List<String> domain;

        public List<String> domain_suffix;

        public List<String> domain_keyword;

        public List<String> domain_regex;

        public List<String> geosite;

        public List<String> source_geoip;

        public List<String> geoip;

        public List<String> ip_cidr;

        public Boolean ip_is_private;

        public Boolean ip_accept_any;

        public List<String> source_ip_cidr;

        public Boolean source_ip_is_private;

        public List<Integer> source_port;

        public List<String> source_port_range;

        public List<Integer> port;

        public List<String> port_range;

        public List<String> process_name;

        public List<String> process_path;

        public List<String> process_path_regex;

        public List<String> package_name;

        public List<String> user;

        public List<Integer> user_id;

        public List<String> outbound;

        public String clash_mode;

        public List<String> network_type;

        public Boolean network_is_expensive;

        public Boolean network_is_constrained;

        public List<String> wifi_ssid;

        public List<String> wifi_bssid;

        public Map<String, List<String>> interface_address;

        public Map<String, List<String>> network_interface_address;

        public List<String> default_interface_address;

        public List<String> rule_set;

        public Boolean rule_set_ip_cidr_match_source;

        public Boolean rule_set_ip_cidr_accept_empty;

        public Boolean invert;

        public Boolean rule_set_ipcidr_match_source;

        // Generate Note: Action
        public String action;

        public String server;

        public String strategy;

        public Boolean disable_cache;

        public Integer rewrite_ttl;

        public String client_subnet;

        public String rcode;

        public List<String> answer;

        public List<String> ns;

        public List<String> extra;

        public String method;

        public Boolean no_drop;

    }

    public static class DNSRule_Logical extends DNSRule {

        // Generate note: nested type RawLogicalDNSRule
        public String mode;

        public List<DNSRule> rules;

        public Boolean invert;

        // Generate Note: Action
        public String action;

        public String server;

        public String strategy;

        public Boolean disable_cache;

        public Integer rewrite_ttl;

        public String client_subnet;

        public String rcode;

        public List<String> answer;

        public List<String> ns;

        public List<String> extra;

        public String method;

        public Boolean no_drop;

    }

    public static class RuleSet_Plain extends RuleSet {

        public List<HeadlessRule> rules;

    }

    public static class RuleSet_Local extends RuleSet {

        public String path;

    }

    public static class RuleSet_Remote extends RuleSet {

        public String url;

        public String download_detour;

        public String update_interval;

    }

    public static class V2RayTransportOptions_V2RayHTTPOptions extends V2RayTransportOptions {

        public List<String> host;

        public String path;

        public String method;

        public Map<String, List<String>> headers;

        public String idle_timeout;

        public String ping_timeout;

    }

    public static class V2RayTransportOptions_V2RayWebsocketOptions extends V2RayTransportOptions {

        public String path;

        public Map<String, List<String>> headers;

        public Integer max_early_data;

        public String early_data_header_name;

    }

    public static class V2RayTransportOptions_V2RayQUICOptions extends V2RayTransportOptions {

    }

    public static class V2RayTransportOptions_V2RayGRPCOptions extends V2RayTransportOptions {

        public String service_name;

        public String idle_timeout;

        public String ping_timeout;

        public Boolean permit_without_stream;

    }

    public static class V2RayTransportOptions_V2RayHTTPUpgradeOptions extends V2RayTransportOptions {

        public String host;

        public String path;

        public Map<String, List<String>> headers;

    }

    public static class Inbound_HTTPMixedOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public String bind_interface;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        public Long udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        public Boolean udp_disable_domain_unmapping;

        public String detour;

        public List<User> users;

        public DomainResolveOptions domain_resolver;

        public Boolean set_system_proxy;

        // Generate note: nested type InboundTLSOptionsContainer
        public InboundTLSOptions tls;

    }

    public static class Inbound_TunOptions extends Inbound {

        public String interface_name;

        public Integer mtu;

        public List<String> address;

        public Boolean auto_route;

        public Integer iproute2_table_index;

        public Integer iproute2_rule_index;

        public Boolean auto_redirect;

        public Integer auto_redirect_input_mark;

        public Integer auto_redirect_output_mark;

        public Boolean exclude_mptcp;

        public List<String> loopback_address;

        public Boolean strict_route;

        public List<String> route_address;

        public List<String> route_address_set;

        public List<String> route_exclude_address;

        public List<String> route_exclude_address_set;

        public List<String> include_interface;

        public List<String> exclude_interface;

        public List<Integer> include_uid;

        public List<String> include_uid_range;

        public List<Integer> exclude_uid;

        public List<String> exclude_uid_range;

        public List<Integer> include_android_user;

        public List<String> include_package;

        public List<String> exclude_package;

        public Long udp_timeout;

        public String stack;

        public Inbound_TunPlatformOptions platform;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        public Boolean udp_disable_domain_unmapping;

        public String detour;

        public Boolean gso;

        public List<String> inet4_address;

        public List<String> inet6_address;

        public List<String> inet4_route_address;

        public List<String> inet6_route_address;

        public List<String> inet4_route_exclude_address;

        public List<String> inet6_route_exclude_address;

        public Boolean endpoint_independent_nat;

    }

    public static class Inbound_TunPlatformOptions extends Inbound {

        public Inbound_HTTPProxyOptions http_proxy;

    }

    public static class Inbound_HTTPProxyOptions extends Inbound {

        public Boolean enabled;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public List<String> bypass_domain;

        public List<String> match_domain;

    }

    public static class Inbound_DirectOptions extends Inbound {

        // Generate note: nested type ListenOptions
        public String listen;

        public Integer listen_port;

        public String bind_interface;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean udp_fragment;

        public Long udp_timeout;

        public Boolean proxy_protocol;

        public Boolean proxy_protocol_accept_no_header;

        // Generate note: nested type InboundOptions
        public Boolean sniff;

        public Boolean sniff_override_destination;

        public String sniff_timeout;

        public String domain_strategy;

        public Boolean udp_disable_domain_unmapping;

        public String detour;

        public String network;

        public String override_address;

        public Integer override_port;

    }

    public static class Outbound_DirectOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        public String override_address;

        public Integer override_port;

        public Integer proxy_protocol;

    }

    public static class Outbound_ShadowsocksOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String method;

        public String password;

        public String plugin;

        public String plugin_opts;

        public String network;

        public UDPOverTCPOptions udp_over_tcp;

        public OutboundMultiplexOptions multiplex;

    }

    public static class Outbound_ShadowTLSOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public Integer version;

        public String password;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

    }

    public static class Outbound_SelectorOptions extends Outbound {

        public List<String> outbounds;

        @SerializedName("default")
        public String default_;

        public Boolean interrupt_exist_connections;

    }

    public static class Outbound_URLTestOptions extends Outbound {

        public List<String> outbounds;

        public String url;

        public String interval;

        public Integer tolerance;

        public String idle_timeout;

        public Boolean interrupt_exist_connections;

    }

    public static class Outbound_SOCKSOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String version;

        public String username;

        public String password;

        public String network;

        public UDPOverTCPOptions udp_over_tcp;

    }

    public static class Outbound_HTTPOptions extends Outbound {

        // Generate note: nested type HTTPOutboundOptions
        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String username;

        public String password;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public String path;

        public Map<String, List<String>> headers;

        public UDPOverTCPOptions udp_over_tcp;

    }

    public static class Outbound_SSHOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String user;

        public String password;

        public List<String> private_key;

        public String private_key_path;

        public String private_key_passphrase;

        public List<String> host_key;

        public List<String> host_key_algorithms;

        public String client_version;

    }

    public static class Outbound_TrojanOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String password;

        public String network;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public OutboundMultiplexOptions multiplex;

        public V2RayTransportOptions transport;

    }

    public static class Outbound_HysteriaOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public List<String> server_ports;

        public String hop_interval;

        public String up;

        public Integer up_mbps;

        public String down;

        public Integer down_mbps;

        public String obfs;

        public String auth;

        public String auth_str;

        public Long recv_window_conn;

        public Long recv_window;

        public Boolean disable_mtu_discovery;

        public String network;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

    }

    public static class Outbound_Hysteria2Options extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public List<String> server_ports;

        public String hop_interval;

        public Integer up_mbps;

        public Integer down_mbps;

        public Hysteria2Obfs obfs;

        public String password;

        public String network;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public Boolean brutal_debug;

    }

    public static class Outbound_TUICOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String uuid;

        public String password;

        public String congestion_control;

        public String udp_relay_mode;

        public Boolean udp_over_stream;

        public Boolean zero_rtt_handshake;

        public String heartbeat;

        public String network;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

    }

    public static class Outbound_VLESSOptions extends Outbound {

        // Generate note: nested type VLESSOutboundOptions
        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String uuid;

        public String flow;

        public String network;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public OutboundMultiplexOptions multiplex;

        public V2RayTransportOptions transport;

        public String packet_encoding;

        public String encryption;

    }

    public static class Outbound_VMessOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String uuid;

        public String security;

        public Integer alter_id;

        public Boolean global_padding;

        public Boolean authenticated_length;

        public String network;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public String packet_encoding;

        public OutboundMultiplexOptions multiplex;

        public V2RayTransportOptions transport;

    }

    public static class Outbound_AnyTLSOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public String password;

        public String idle_session_check_interval;

        public String idle_session_timeout;

        public Integer min_idle_session;

    }

    public static class Outbound_JuicityOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String uuid;

        public String password;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public String pin_cert_sha256;

    }

    public static class Outbound_NaiveOptions extends Outbound {

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type ServerOptions
        public String server;

        public Integer server_port;

        public String username;

        public String password;

        public Integer insecure_concurrency;

        public Map<String, List<String>> extra_headers;

        public UDPOverTCPOptions udp_over_tcp;

        public Boolean quic;

        public String quic_congestion_control;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

    }

    public static class Endpoint_WireGuardOptions extends Endpoint {

        public Boolean system;

        public String name;

        public Integer mtu;

        public List<String> address;

        public String private_key;

        public Integer listen_port;

        public List<WireGuardPeer> peers;

        public String udp_timeout;

        public Integer workers;

        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

    }

    public static class NewDNSServerOptions_HostsDNSServerOptions extends NewDNSServerOptions {

        public List<String> path;

        public Map<String, List<String>> predefined;

    }

    public static class NewDNSServerOptions_LocalDNSServerOptions extends NewDNSServerOptions {

        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        public Boolean prefer_go;

    }

    public static class NewDNSServerOptions_RemoteDNSServerOptions extends NewDNSServerOptions {

        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type DNSServerAddressOptions
        public String server;

        public Integer server_port;

    }

    public static class NewDNSServerOptions_RemoteTCPDNSServerOptions extends NewDNSServerOptions {

        // Generate note: nested type RemoteDNSServerOptions
        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type DNSServerAddressOptions
        public String server;

        public Integer server_port;

        public Boolean reuse;

    }

    public static class NewDNSServerOptions_RemoteTLSDNSServerOptions extends NewDNSServerOptions {

        // Generate note: nested type RemoteDNSServerOptions
        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type DNSServerAddressOptions
        public String server;

        public Integer server_port;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

    }

    public static class NewDNSServerOptions_RemoteHTTPSDNSServerOptions extends NewDNSServerOptions {

        // Generate note: nested type RemoteTLSDNSServerOptions
        // Generate note: nested type RemoteDNSServerOptions
        // Generate note: nested type RawLocalDNSServerOptions
        // Generate note: nested type DialerOptions
        public String detour;

        public String bind_interface;

        public String inet4_bind_address;

        public String inet6_bind_address;

        public String protect_path;

        public Integer routing_mark;

        public Boolean reuse_addr;

        public String netns;

        public String connect_timeout;

        public Boolean tcp_fast_open;

        public Boolean tcp_multi_path;

        public Boolean disable_tcp_keep_alive;

        public String tcp_keep_alive;

        public String tcp_keep_alive_interval;

        public Boolean udp_fragment;

        public DomainResolveOptions domain_resolver;

        public String network_strategy;

        public List<String> network_type;

        public List<String> fallback_network_type;

        public String fallback_delay;

        public String domain_strategy;

        // Generate note: nested type DNSServerAddressOptions
        public String server;

        public Integer server_port;

        // Generate note: nested type OutboundTLSOptionsContainer
        public OutboundTLSOptions tls;

        public String path;

        public String method;

        public Map<String, List<String>> headers;

    }

    public static class NewDNSServerOptions_FakeIPDNSServerOptions extends NewDNSServerOptions {

        public String inet4_range;

        public String inet6_range;

    }

}
