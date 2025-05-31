package io.nekohasekai.sagernet.fmt

class RuleItem(val type: String = "", val content: String) {
    companion object {
        const val TYPE_FLAG_PLUS_DNS = "+dns"
        const val TYPE_FLAG_MINUS_DNS = "-dns"

        const val TYPE_FLAG_RULE_SET = "set"
        const val TYPE_FLAG_FULL = "full"
        const val TYPE_FLAG_DOMAIN_SUFFIX = "domain"
        const val TYPE_FLAG_REGEX = "regexp"

        private fun validType(type: String) = when (type) {
            "",
            TYPE_FLAG_RULE_SET, TYPE_FLAG_FULL,
            TYPE_FLAG_DOMAIN_SUFFIX, TYPE_FLAG_REGEX -> true

            else -> false
        }

        // Set this value is same as enable `ip_is_private`.
        const val CONTENT_PRIVATE = "private"

        // Set this value is same as enable `ip_accept_any`.
        const val CONTENT_ANY = "any"

        fun parseRule(raw: String, defaultDNSBehavior: Boolean): RuleItem {
            val parts = raw.split(":", limit = 2)

            if (parts.size == 1) return RuleItem(content = raw)

            var dns: Boolean? = null
            val type = parts[0].tryRemoveSuffix(TYPE_FLAG_PLUS_DNS)?.also {
                dns = true
            } ?: parts[0].tryRemoveSuffix(TYPE_FLAG_MINUS_DNS)?.also {
                dns = false
            } ?: parts[0]
            if (!validType(type)) return RuleItem(content = raw)

            return RuleItem(type, parts[1]).also {
                it.dns = dns ?: defaultDNSBehavior
            }
        }

        fun parseRules(list: List<String>, defaultDNSBehavior: Boolean): List<RuleItem> {
            val rules = ArrayList<RuleItem>(list.size)
            for (raw in list) rules.add(parseRule(raw, defaultDNSBehavior))
            return rules
        }

        private fun String.tryRemoveSuffix(suffix: String): String? = if (this.endsWith(suffix)) {
            this.substring(0, this.length - suffix.length)
        } else {
            null
        }
    }

    var dns: Boolean = false
}
