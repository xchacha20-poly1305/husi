package moe.matsuri.nb4a

fun parseRule(raw: String): RuleItem {
    val parts = raw.split(":", limit = 2)

    if (parts.size == 1) return RuleItem(content = raw)

    var dns = false
    var type = ""
    for (prefix in parts[0].split(RuleItem.TYPE_SPLIT_FLAG)) when (prefix) {
        RuleItem.TYPE_FLAG_DNS -> dns = true
        // Setting too much prefix tags is undefined behavior.
        else -> type = prefix
    }

    return RuleItem(type, parts[1]).also {
        it.dns = dns
    }
}

fun parseRules(list: List<String>): List<RuleItem> {
    val rules = ArrayList<RuleItem>(list.size)
    for (raw in list) rules.add(parseRule(raw))
    return rules
}

class RuleItem(val type: String = "", val content: String) {
    companion object {
        const val TYPE_SPLIT_FLAG = "+"

        const val TYPE_FLAG_DNS = "dns"
        const val TYPE_FLAG_RULE_SET = "set"
        const val TYPE_FLAG_FULL = "full"
        const val TYPE_FLAG_DOMAIN_SUFFIX = "domain"
        const val TYPE_FLAG_REGEX = "regexp"

        // Set this value is same as enable `ip_is_private`.
        const val CONTENT_PRIVATE = "private"
    }

    var dns: Boolean = false
}