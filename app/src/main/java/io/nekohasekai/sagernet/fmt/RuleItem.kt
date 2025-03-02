package io.nekohasekai.sagernet.fmt


class RuleItem(val type: String = "", val content: String) {
    companion object {
        const val TYPE_SPLIT_FLAG = "+"

        const val TYPE_FLAG_DNS = "dns"
        const val TYPE_FLAG_RULE_SET = "set"
        const val TYPE_FLAG_FULL = "full"
        const val TYPE_FLAG_DOMAIN_SUFFIX = "domain"
        const val TYPE_FLAG_REGEX = "regexp"

        private fun validType(type: String) = when (type) {
            TYPE_FLAG_RULE_SET, TYPE_FLAG_FULL,
            TYPE_FLAG_DOMAIN_SUFFIX, TYPE_FLAG_REGEX -> true

            else -> false
        }

        // Set this value is same as enable `ip_is_private`.
        const val CONTENT_PRIVATE = "private"

        fun parseRule(raw: String): RuleItem {
            val parts = raw.split(":", limit = 2)

            if (parts.size == 1) return RuleItem(content = raw)

            var dns = false
            var type = ""
            var content = ""
            for (prefix in parts[0].split(TYPE_SPLIT_FLAG)) when (prefix) {
                TYPE_FLAG_DNS -> dns = true
                // Setting too much prefix tags is undefined behavior.
                else -> if (validType(prefix)) {
                    type = prefix
                    content = parts[1]
                } else {
                    // IPv6 or port range, which also use ":".
                    content = raw
                }
            }

            return RuleItem(type, content).also {
                it.dns = dns
            }
        }

        fun parseRules(list: List<String>): List<RuleItem> {
            val rules = ArrayList<RuleItem>(list.size)
            for (raw in list) rules.add(parseRule(raw))
            return rules
        }
    }

    /**
     * Just for DNS rule.
     * */
    var dns: Boolean = false
}
