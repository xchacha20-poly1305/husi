package moe.matsuri.nb4a

import io.nekohasekai.sagernet.database.DataStore
import moe.matsuri.nb4a.SingBoxOptions.DNSRule_Default
import moe.matsuri.nb4a.SingBoxOptions.DNSRule_Logical
import moe.matsuri.nb4a.SingBoxOptions.RULE_SET_FORMAT_BINARY
import moe.matsuri.nb4a.SingBoxOptions.RULE_SET_TYPE_LOCAL
import moe.matsuri.nb4a.SingBoxOptions.RULE_SET_TYPE_REMOTE
import moe.matsuri.nb4a.SingBoxOptions.Rule_Default
import moe.matsuri.nb4a.SingBoxOptions.Rule_Logical

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

fun DNSRule_Default.makeCommonRule(list: List<RuleItem>) {
    domain = mutableListOf()
    domain_suffix = mutableListOf()
    domain_regex = mutableListOf()
    domain_keyword = mutableListOf()
    rule_set = mutableListOf()

    for (rule in list) {
        if (rule.content == RuleItem.CONTENT_PRIVATE) {
            ip_is_private = true
            continue
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
    if (rule_set?.isEmpty() == true) rule_set = null
    if (domain?.isEmpty() == true) domain = null
    if (domain_suffix?.isEmpty() == true) domain_suffix = null
    if (domain_regex?.isEmpty() == true) domain_regex = null
    if (domain_keyword?.isEmpty() == true) domain_keyword = null
}

fun DNSRule_Default.checkEmpty(): Boolean {
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
            if (rule.content == RuleItem.CONTENT_PRIVATE) {
                ip_is_private = true
                continue
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
    return true
}

/**
 * Builds all rule-set.
 * This will crate route if route is null,
 * and will refreshes route.rule_set.
 * */
fun SingBoxOptions.MyOptions.buildRuleSets(
    ipURL: String?,
    domainURL: String?,
    localPath: String?,
) {
    val names = hashSetOf<String>()
    if (dns != null) names.addAll(collectSet(dns.rules))
    if (route != null) names.addAll(collectSet(route.rules))

    if (names.isEmpty()) return

    if (route == null) route = SingBoxOptions.RouteOptions()
    for (set in route.rule_set) names.add(set.tag)
    val list = ArrayList<SingBoxOptions.RuleSet>(names.size)

    val isRemote = ipURL != null
    for (name in names.sorted()) {
        if (isRemote) list.add(SingBoxOptions.RuleSet_Remote().apply {
            tag = name
            type = RULE_SET_TYPE_REMOTE
            format = RULE_SET_FORMAT_BINARY
            val isIP = name.startsWith("geoip-")
            url = if (isIP) {
                "${ipURL}/${name}.srs"
            } else {
                "${domainURL}/${name}.srs"
            }
        }) else list.add(SingBoxOptions.RuleSet_Local().apply {
            tag = name
            type = RULE_SET_TYPE_LOCAL
            format = RULE_SET_FORMAT_BINARY
            path = "$localPath/$name.srs"
        })
    }

    route.rule_set = list
}

/**
 * Collects all rule-set in rules.
 * @param rules item should be DNSRule or Rule.
 */
private fun collectSet(rules: List<SingBoxOptions.SingBoxOption>?): HashSet<String> {
    if (rules == null) return hashSetOf()

    val hashSet = hashSetOf<String>()
    for (rule in rules) when (rule) {
        is DNSRule_Logical -> hashSet.addAll(collectSet(rule.rules))
        is Rule_Logical -> hashSet.addAll(collectSet(rule.rules))

        is DNSRule_Default -> rule.rule_set?.let {
            hashSet.addAll(it)
        }

        is Rule_Default -> rule.rule_set?.let {
            hashSet.addAll(it)
        }
    }

    return hashSet
}