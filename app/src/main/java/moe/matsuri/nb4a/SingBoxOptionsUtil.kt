package moe.matsuri.nb4a

import io.nekohasekai.sagernet.database.DataStore
import moe.matsuri.nb4a.SingBoxOptions.Rule_SetOptions

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

fun SingBoxOptions.DNSRule_DefaultOptions.makeSingBoxRule(
    basicList: List<String>,
    ruleSetList: List<String>,
) {
    domain = mutableListOf<String>()
    domain_suffix = mutableListOf<String>()
    domain_regex = mutableListOf<String>()
    domain_keyword = mutableListOf<String>()
    rule_set = mutableListOf<String>()
    wifi_ssid = mutableListOf<String>()
    wifi_bssid = mutableListOf<String>()
    ruleSetList.forEach {
        if (it.startsWith("geosite-")) rule_set.plusAssign(it)
    }
    basicList.forEach {
        if (it.startsWith("full:")) {
            domain.plusAssign(it.removePrefix("full:").lowercase())
        } else if (it.startsWith("domain:")) {
            domain_suffix.plusAssign(it.removePrefix("domain:").lowercase())
        } else if (it.startsWith("regexp:")) {
            domain_regex.plusAssign(it.removePrefix("regexp:").lowercase())
        } else if (it.startsWith("keyword:")) {
            domain_keyword.plusAssign(it.removePrefix("keyword:").lowercase())
        } else {
            // https://github.com/SagerNet/sing-box/commit/5d41e328d4a9f7549dd27f11b4ccc43710a73664
            domain.plusAssign(it.lowercase())
        }
    }
    rule_set?.removeIf { it.isNullOrBlank() }
    domain?.removeIf { it.isNullOrBlank() }
    domain_suffix?.removeIf { it.isNullOrBlank() }
    domain_regex?.removeIf { it.isNullOrBlank() }
    domain_keyword?.removeIf { it.isNullOrBlank() }
    wifi_ssid?.removeIf { it.isNullOrBlank() }
    wifi_bssid?.removeIf { it.isNullOrBlank() }
    if (rule_set?.isEmpty() == true) rule_set = null
    if (domain?.isEmpty() == true) domain = null
    if (domain_suffix?.isEmpty() == true) domain_suffix = null
    if (domain_regex?.isEmpty() == true) domain_regex = null
    if (domain_keyword?.isEmpty() == true) domain_keyword = null
    if (wifi_ssid?.isEmpty() == true) wifi_ssid = null
    if (wifi_bssid?.isEmpty() == true) wifi_bssid = null
}

fun SingBoxOptions.DNSRule_DefaultOptions.checkEmpty(): Boolean {
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

fun SingBoxOptions.Rule_DefaultOptions.makeSingBoxRule(list: List<String>, isIP: Boolean) {
    if (isIP) {
        ip_cidr = mutableListOf<String>()
    } else {
        domain = mutableListOf<String>()
        domain_suffix = mutableListOf<String>()
        domain_regex = mutableListOf<String>()
        domain_keyword = mutableListOf<String>()
    }
    rule_set = mutableListOf<String>()
    list.forEach {
        if (isIP) {
            ip_cidr.plusAssign(it)
            return@forEach
        }
        if (it.startsWith("full:")) {
            domain.plusAssign(it.removePrefix("full:").lowercase())
        } else if (it.startsWith("domain:")) {
            domain_suffix.plusAssign(it.removePrefix("domain:").lowercase())
        } else if (it.startsWith("regexp:")) {
            domain_regex.plusAssign(it.removePrefix("regexp:").lowercase())
        } else if (it.startsWith("keyword:")) {
            domain_keyword.plusAssign(it.removePrefix("keyword:").lowercase())
        } else {
            // https://github.com/SagerNet/sing-box/commit/5d41e328d4a9f7549dd27f11b4ccc43710a73664
            domain.plusAssign(it.lowercase())
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
}

fun SingBoxOptions.Rule_DefaultOptions.checkEmpty(): Boolean {
    if (ip_cidr?.isNotEmpty() == true) return false
    if (rule_set?.isNotEmpty() == true) return false
    if (domain?.isNotEmpty() == true) return false
    if (domain_suffix?.isNotEmpty() == true) return false
    if (domain_regex?.isNotEmpty() == true) return false
    if (domain_keyword?.isNotEmpty() == true) return false
    if (user_id?.isNotEmpty() == true) return false
    //
    if (port?.isNotEmpty() == true) return false
    if (port_range?.isNotEmpty() == true) return false
    if (source_ip_cidr?.isNotEmpty() == true) return false
    if (wifi_ssid?.isNotEmpty() == true) return false
    if (wifi_bssid?.isNotEmpty() == true) return false
    return true
}

fun SingBoxOptions.RouteOptions.makeSingBoxRuleSet(list: List<String>, geoPath: String) {
    if (rule_set == null) rule_set = mutableListOf<Rule_SetOptions>()
    list.forEach {
        rule_set.add(Rule_SetOptions().apply {
            tag = it
            type = "local"
            format = "binary"
            path = "$geoPath/$it.srs"
        })
    }
}

fun List<Rule_SetOptions>.distinctByTag(): List<Rule_SetOptions> {
    return this.distinctBy { it.tag }
}

fun Rule_SetOptions.checkEmpty(): Boolean {
    // ???
    if (tag?.isNotEmpty() == true) return false
    if (type?.isNotEmpty() == true) return false
    if (format?.isNotEmpty() == true) return false

    return true
}
