package moe.matsuri.nb4a

import io.nekohasekai.sagernet.DNSMode
import io.nekohasekai.sagernet.database.DataStore

object SingBoxOptionsUtil {

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

}

// Set this value is same as enable `ip_is_private`.
const val IP_PRIVATE = "private"

// Starts with it will apply this IP rule for DNS.
const val PREFIX_IP_DNS = "dns:"

fun SingBoxOptions.DNSRule_Default.makeSingBoxRule(
    domainList: List<String>,
    ruleSetList: List<String>,
    dnsMode: Int = DNSMode.RAW,
    ipList: List<String>,
) {
    val shouldApplyIPRule = dnsMode == DNSMode.LEAK || dnsMode == DNSMode.PRECISE

    domain = mutableListOf<String>()
    domain_suffix = mutableListOf<String>()
    domain_regex = mutableListOf<String>()
    domain_keyword = mutableListOf<String>()
    rule_set = mutableListOf<String>()
    wifi_ssid = mutableListOf<String>()
    wifi_bssid = mutableListOf<String>()

    for (rule in ruleSetList) {
        if (rule.startsWith("geosite-")) {
            rule_set.plusAssign(rule)
            continue
        }
        if (shouldApplyIPRule) {
            if (rule.startsWith(PREFIX_IP_DNS)) {
                rule_set.plusAssign(rule.removePrefix(PREFIX_IP_DNS))
            }
        }
    }

    for (rule in domainList) {
        if (rule.startsWith("full:")) {
            domain.plusAssign(rule.removePrefix("full:").lowercase())
        } else if (rule.startsWith("domain:")) {
            domain_suffix.plusAssign(rule.removePrefix("domain:").lowercase())
        } else if (rule.startsWith("regexp:")) {
            domain_regex.plusAssign(rule.removePrefix("regexp:").lowercase())
        } else {
            domain_keyword.plusAssign(rule.lowercase())
        }
    }

    if (shouldApplyIPRule) for (rule in ipList) {
        if (rule.startsWith(PREFIX_IP_DNS)) rule.removePrefix(PREFIX_IP_DNS).let {
            if (it == IP_PRIVATE) {
                ip_is_private = true
            } else {
                ip_cidr.plusAssign(it)
            }
        }
    }

    rule_set?.removeIf { it.isNullOrBlank() }
    domain?.removeIf { it.isNullOrBlank() }
    domain_suffix?.removeIf { it.isNullOrBlank() }
    domain_regex?.removeIf { it.isNullOrBlank() }
    domain_keyword?.removeIf { it.isNullOrBlank() }
    wifi_ssid?.removeIf { it.isNullOrBlank() }
    wifi_bssid?.removeIf { it.isNullOrBlank() }

    if (ip_is_private == false) ip_is_private = null
    if (rule_set?.isEmpty() == true) rule_set = null
    if (domain?.isEmpty() == true) domain = null
    if (domain_suffix?.isEmpty() == true) domain_suffix = null
    if (domain_regex?.isEmpty() == true) domain_regex = null
    if (domain_keyword?.isEmpty() == true) domain_keyword = null
    if (wifi_ssid?.isEmpty() == true) wifi_ssid = null
    if (wifi_bssid?.isEmpty() == true) wifi_bssid = null
}

fun SingBoxOptions.DNSRule_Default.checkEmpty(): Boolean {
    if (ip_is_private == true) return false
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

fun SingBoxOptions.Rule_Default.makeSingBoxRule(rules: List<String>, isIP: Boolean) {
    if (isIP) {
        ip_cidr = mutableListOf<String>()
    } else {
        domain = mutableListOf<String>()
        domain_suffix = mutableListOf<String>()
        domain_regex = mutableListOf<String>()
        domain_keyword = mutableListOf<String>()
    }
    if (rule_set == null) rule_set = mutableListOf<String>()

    for (rule in rules) {
        if (isIP) {
            if (rule.startsWith(PREFIX_IP_DNS)) continue

            if (rule == IP_PRIVATE) {
                ip_is_private = true
            } else {
                ip_cidr.plusAssign(rule)
            }
            continue
        }

        if (rule.startsWith("full:")) {
            domain.plusAssign(rule.removePrefix("full:").lowercase())
        } else if (rule.startsWith("domain:")) {
            domain_suffix.plusAssign(rule.removePrefix("domain:").lowercase())
        } else if (rule.startsWith("regexp:")) {
            domain_regex.plusAssign(rule.removePrefix("regexp:").lowercase())
        } else {
            domain_keyword.plusAssign(rule.lowercase())
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

fun SingBoxOptions.Rule_Default.checkEmpty(): Boolean {
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
    return true
}

fun SingBoxOptions.RouteOptions.makeSingBoxRuleSet(names: List<String>, geoPath: String) {
    if (rule_set == null) rule_set = mutableListOf<SingBoxOptions.RuleSet>()
    for (name in names) {
        val newName = name.removePrefix(PREFIX_IP_DNS)
        rule_set.plusAssign(SingBoxOptions.RuleSet_Local().apply {
            tag = newName
            type = "local"
            format = "binary"
            path = "$geoPath/$newName.srs"
        })
    }
}