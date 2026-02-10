package fr.husi.fmt

import fr.husi.fmt.SingBoxOptions.DomainResolveOptions
import fr.husi.fmt.SingBoxOptions.ExperimentalOptions
import fr.husi.fmt.SingBoxOptions.LogOptions
import fr.husi.fmt.SingBoxOptions.MyDNSOptions
import fr.husi.fmt.SingBoxOptions.MyOptions
import fr.husi.fmt.SingBoxOptions.MyRouteOptions
import fr.husi.fmt.SingBoxOptions.NTPOptions
import fr.husi.ktx.toJsonObjectKxs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable as KxsSerializable
import kotlinx.serialization.json.JsonObject

@KxsSerializable
data class MyDNSOptionsKxs(
    val servers: List<JsonObject>? = null,
    val rules: List<JsonObject>? = null,
    @SerialName("final")
    val final_: String? = null,
    val reverse_mapping: Boolean? = null,
    val strategy: String? = null,
    val disable_cache: Boolean? = null,
    val disable_expire: Boolean? = null,
    val independent_cache: Boolean? = null,
    val cache_capacity: Int? = null,
    val client_subnet: String? = null,
)

@KxsSerializable
data class MyRouteOptionsKxs(
    val rules: List<JsonObject>? = null,
    val rule_set: List<JsonObject>? = null,
    @SerialName("final")
    val final_: String? = null,
    val find_process: Boolean? = null,
    val auto_detect_interface: Boolean? = null,
    val override_android_vpn: Boolean? = null,
    val default_interface: String? = null,
    val default_mark: Int? = null,
    val default_domain_resolver: DomainResolveOptions? = null,
    val default_network_strategy: String? = null,
    val default_network_type: List<String>? = null,
    val default_fallback_network_type: List<String>? = null,
    val default_fallback_delay: String? = null,
)

@KxsSerializable
data class MyOptionsKxs(
    val log: LogOptions? = null,
    val dns: MyDNSOptionsKxs? = null,
    val ntp: NTPOptions? = null,
    val inbounds: List<JsonObject>? = null,
    val outbounds: List<JsonObject>? = null,
    val endpoints: List<JsonObject>? = null,
    val route: MyRouteOptionsKxs? = null,
    val experimental: ExperimentalOptions? = null,
)

fun MyDNSOptions.toKxs(): MyDNSOptionsKxs = MyDNSOptionsKxs(
    servers = servers?.map { it.toJsonObjectKxs() },
    rules = rules?.map { it.toJsonObjectKxs() },
    final_ = final_,
    reverse_mapping = reverse_mapping,
    strategy = strategy,
    disable_cache = disable_cache,
    disable_expire = disable_expire,
    independent_cache = independent_cache,
    cache_capacity = cache_capacity,
    client_subnet = client_subnet,
)

fun MyRouteOptions.toKxs(): MyRouteOptionsKxs = MyRouteOptionsKxs(
    rules = rules?.map { it.toJsonObjectKxs() },
    rule_set = rule_set?.map { it.toJsonObjectKxs() },
    final_ = final_,
    find_process = find_process,
    auto_detect_interface = auto_detect_interface,
    override_android_vpn = override_android_vpn,
    default_interface = default_interface,
    default_mark = default_mark,
    default_domain_resolver = default_domain_resolver,
    default_network_strategy = default_network_strategy,
    default_network_type = default_network_type,
    default_fallback_network_type = default_fallback_network_type,
    default_fallback_delay = default_fallback_delay,
)

fun MyOptions.toKxs(): MyOptionsKxs = MyOptionsKxs(
    log = log,
    dns = dns?.toKxs(),
    ntp = ntp,
    inbounds = inbounds?.map { it.toJsonObjectKxs() },
    outbounds = outbounds?.map { it.toJsonObjectKxs() },
    endpoints = endpoints?.map { it.toJsonObjectKxs() },
    route = route?.toKxs(),
    experimental = experimental,
)
