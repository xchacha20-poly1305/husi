package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSRule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSRule_Logical
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DomainResolveOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.MyOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions_LocalDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions_RemoteDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions_RemoteHTTPSDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions_RemoteTLSDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.OutboundTLSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RouteOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet_Local
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RuleSet_Remote
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Logical
import libcore.Libcore

fun domainStrategy(tag: String): String {
    fun auto2AsIs(key: String): String {
        return (DataStore.configurationStore.getString(key) ?: "").replace("auto", "")
    }
    return when (tag) {
        "dns-remote" -> {
            auto2AsIs("domain_strategy_for_remote")
        }

        "dns-direct" -> {
            auto2AsIs("domain_strategy_for_direct")
        }

        // "server"
        else -> {
            auto2AsIs("domain_strategy_for_server")
        }
    }
}


fun DNSRule_Default.makeCommonRule(list: List<RuleItem>) {
    domain = mutableListOf()
    domain_suffix = mutableListOf()
    domain_regex = mutableListOf()
    domain_keyword = mutableListOf()
    rule_set = mutableListOf()

    for (rule in list) {
        when (rule.content) {
            RuleItem.CONTENT_ANY -> {
                ip_accept_any = true
                continue
            }

            RuleItem.CONTENT_PRIVATE -> {
                ip_is_private = true
                continue
            }
        }

        when (rule.type) {
            RuleItem.TYPE_FLAG_RULE_SET -> rule_set.add(rule.content)
            RuleItem.TYPE_FLAG_FULL -> domain.add(rule.content)
            RuleItem.TYPE_FLAG_DOMAIN_SUFFIX -> domain_suffix.add(rule.content)
            RuleItem.TYPE_FLAG_REGEX -> domain_regex.add(rule.content)
            else -> domain_keyword.add(rule.content)
        }
    }

    rule_set?.removeIf { it.isNullOrBlank() }
    domain?.removeIf { it.isNullOrBlank() }
    domain_suffix?.removeIf { it.isNullOrBlank() }
    domain_regex?.removeIf { it.isNullOrBlank() }
    domain_keyword?.removeIf { it.isNullOrBlank() }

    if (ip_is_private == false) ip_is_private = null
    if (ip_accept_any == false) ip_accept_any = null
    if (rule_set?.isEmpty() == true) rule_set = null
    if (domain?.isEmpty() == true) domain = null
    if (domain_suffix?.isEmpty() == true) domain_suffix = null
    if (domain_regex?.isEmpty() == true) domain_regex = null
    if (domain_keyword?.isEmpty() == true) domain_keyword = null
}

fun DNSRule_Default.checkEmpty(): Boolean {
    if (ip_is_private == true) return false
    if (ip_accept_any == true) return false
    if (rule_set?.isNotEmpty() == true) return false
    if (domain?.isNotEmpty() == true) return false
    if (domain_suffix?.isNotEmpty() == true) return false
    if (domain_regex?.isNotEmpty() == true) return false
    if (domain_keyword?.isNotEmpty() == true) return false
    if (user_id?.isNotEmpty() == true) return false
    if (wifi_ssid?.isNotEmpty() == true) return false
    if (wifi_bssid?.isNotEmpty() == true) return false
    return true
}

fun Rule_Default.makeCommonRule(list: List<RuleItem>, isIP: Boolean) {
    if (isIP) {
        ip_cidr = mutableListOf()
    } else {
        domain = mutableListOf()
        domain_suffix = mutableListOf()
        domain_regex = mutableListOf()
        domain_keyword = mutableListOf()
    }
    if (rule_set == null) rule_set = mutableListOf()

    for (rule in list) {
        if (rule.dns) continue

        if (isIP) {
            when (rule.content) {
                RuleItem.CONTENT_ANY -> continue // just for DNS
                RuleItem.CONTENT_PRIVATE -> {
                    source_ip_is_private = true
                    continue
                }
            }
            when (rule.type) {
                RuleItem.TYPE_FLAG_RULE_SET -> rule_set.add(rule.content)
                else -> ip_cidr.add(rule.content)
            }
        } else {
            when (rule.type) {
                RuleItem.TYPE_FLAG_RULE_SET -> rule_set.add(rule.content)
                RuleItem.TYPE_FLAG_FULL -> domain.add(rule.content)
                RuleItem.TYPE_FLAG_DOMAIN_SUFFIX -> domain_suffix.add(rule.content)
                RuleItem.TYPE_FLAG_REGEX -> domain_regex.add(rule.content)
                else -> domain_keyword.add(rule.content)
            }
        }
    }

    rule_set?.removeIf { it.isNullOrBlank() }
    ip_cidr?.removeIf { it.isNullOrBlank() }
    domain?.removeIf { it.isNullOrBlank() }
    domain_suffix?.removeIf { it.isNullOrBlank() }
    domain_regex?.removeIf { it.isNullOrBlank() }
    domain_keyword?.removeIf { it.isNullOrBlank() }
    if (rule_set?.isEmpty() == true) rule_set = null
    if (ip_cidr?.isEmpty() == true) ip_cidr = null
    if (domain?.isEmpty() == true) domain = null
    if (domain_suffix?.isEmpty() == true) domain_suffix = null
    if (domain_regex?.isEmpty() == true) domain_regex = null
    if (domain_keyword?.isEmpty() == true) domain_keyword = null
    if (ip_is_private == false) ip_is_private = null
}

fun Rule_Default.checkEmpty(): Boolean {
    if (ip_cidr?.isNotEmpty() == true) return false
    if (rule_set?.isNotEmpty() == true) return false
    if (ip_is_private == true) return false
    if (source_ip_is_private == true) return false

    if (domain?.isNotEmpty() == true) return false
    if (domain_suffix?.isNotEmpty() == true) return false
    if (domain_regex?.isNotEmpty() == true) return false
    if (domain_keyword?.isNotEmpty() == true) return false
    if (user_id?.isNotEmpty() == true) return false

    if (port?.isNotEmpty() == true) return false
    if (port_range?.isNotEmpty() == true) return false
    if (source_ip_cidr?.isNotEmpty() == true) return false
    if (wifi_ssid?.isNotEmpty() == true) return false
    if (wifi_bssid?.isNotEmpty() == true) return false
    if (clash_mode?.isNotEmpty() == true) return false
    if (network_type?.isNotEmpty() == true) return false
    if (network_is_expensive == true) return false

    if (override_address?.isNotEmpty() == true) return false
    if (override_port != null && override_port > 0) return false
    if (tls_fragment == true) return false
    if (tls_record_fragment == true) return false

    if (strategy != null) return false
    if (disable_cache == true) return false
    if (rewrite_ttl != null) return false
    if (client_subnet?.isNotEmpty() == true) return false

    if (timeout?.isNotEmpty() == true) return false
    if (sniffer?.isNotEmpty() == true) return false

    return true
}

/**
 * Builds all rule-set.
 * This will crate route if route is null,
 * and will refreshes route.rule_set.
 * */
fun MyOptions.buildRuleSets(
    ipURL: String?,
    domainURL: String?,
    localPath: String?,
) {
    val names = hashSetOf<String>()
    if (dns != null) collectSet(names, dns.rules)
    if (route != null) collectSet(names, route.rules)

    if (names.isEmpty()) return

    if (route == null) route = RouteOptions()
    if (route.rule_set == null) route.rule_set = emptyList()
    for (set in route.rule_set) names.add(set.tag)
    val list = ArrayList<RuleSet>(names.size)

    val isRemote = ipURL != null
    for (name in names.sorted()) {
        if (isRemote) list.add(RuleSet_Remote().apply {
            tag = name
            type = SingBoxOptions.RULE_SET_TYPE_REMOTE
            val isIP = name.startsWith("geoip-")
            url = if (isIP) {
                "${ipURL}/${name}.srs"
            } else {
                "${domainURL}/${name}.srs"
            }
        }) else list.add(RuleSet_Local().apply {
            tag = name
            type = SingBoxOptions.RULE_SET_TYPE_LOCAL
            path = "$localPath/$name.srs"
        })
    }

    route.rule_set = list
}

/**
 * Collects all rule-set in rules.
 * @param rules item should be DNSRule or Rule.
 */
private fun collectSet(set: HashSet<String>, rules: List<SingBoxOptions.SingBoxOption>?) {
    if (rules == null) return

    for (rule in rules) when (rule) {
        is DNSRule_Logical -> collectSet(set, rule.rules)
        is Rule_Logical -> collectSet(set, rule.rules)

        is DNSRule_Default -> rule.rule_set?.let {
            set.addAll(it)
        }

        is Rule_Default -> rule.rule_set?.let {
            set.addAll(it)
        }
    }

    return
}

fun isEndpoint(type: String): Boolean = when (type) {
    SingBoxOptions.TYPE_WIREGUARD -> true
    else -> false
}

/**
 * Turn link to new DNS options.
 */
fun buildDNSServer(
    link: String,
    out: String?,
    tag: String,
    domainResolver: DomainResolveOptions,
): NewDNSServerOptions {
    if (link == "local") return NewDNSServerOptions_LocalDNSServerOptions().also {
        it.type = SingBoxOptions.DNS_TYPE_LOCAL
        it.tag = tag
        it.domain_resolver = domainResolver
    }

    val url = if (!link.contains("://")) {
        Libcore.newURL(SingBoxOptions.DNS_TYPE_UDP).apply {
            fullHost = link
        }
    } else {
        Libcore.parseURL(link)
    }

    return when (val scheme = url.scheme) {
        SingBoxOptions.DNS_TYPE_TLS, SingBoxOptions.DNS_TYPE_QUIC -> NewDNSServerOptions_RemoteTLSDNSServerOptions().apply {
            type = scheme
            server = url.host
            server_port = url.ports.toIntOrNull()
            domain_resolver = domainResolver
            tls = OutboundTLSOptions().apply {
                enabled = true
            }
            detour = out
        }

        "http3", SingBoxOptions.DNS_TYPE_HTTPS, SingBoxOptions.DNS_TYPE_H3 -> NewDNSServerOptions_RemoteHTTPSDNSServerOptions().apply {
            type = if (scheme == "http3") {
                SingBoxOptions.DNS_TYPE_H3
            } else {
                scheme
            }
            server = url.host
            server_port = url.ports.toIntOrNull()
            domain_resolver = domainResolver
            tls = OutboundTLSOptions().apply {
                enabled = true
            }
            path = url.path
            detour = out
        }

        // "", SingBoxOptions.DNS_TYPE_UDP, SingBoxOptions.DNS_TYPE_TCP ->
        else -> NewDNSServerOptions_RemoteDNSServerOptions().apply {
            type = scheme.ifBlank {
                SingBoxOptions.DNS_TYPE_UDP
            }
            server = url.host
            server_port = url.ports.toIntOrNull()
            domain_resolver = domainResolver
            detour = out
        }

    }.also {
        it.tag = tag
    }
}