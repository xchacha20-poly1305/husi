package fr.husi.ui.dashboard

import androidx.compose.runtime.Immutable

enum class ProxySetSearchMode {
    Outbound,
    ProxySet;

    fun toggled() = when (this) {
        Outbound -> ProxySet
        ProxySet -> Outbound
    }
}

@Immutable
data class ProxySetQuery(
    val globalSearch: String = "",
    val groupSearch: String = "",
    val mode: ProxySetSearchMode = ProxySetSearchMode.Outbound,
    val searchingProxyGroup: String? = null,
) {
    val hasGlobalQuery: Boolean
        get() = globalSearch.isNotBlank()

    fun forcesExpanded(set: ProxySet): Boolean {
        if (searchingProxyGroup != null) {
            return searchingProxyGroup == set.id
        }
        return mode == ProxySetSearchMode.Outbound && hasGlobalQuery
    }
}

fun List<ProxySet>.filterBy(query: ProxySetQuery): List<ProxySet> {
    query.searchingProxyGroup?.let { searchingGroup ->
        val keywords = keywordsOf(query.groupSearch)
        if (keywords.isEmpty()) return this
        return map { set ->
            if (set.id != searchingGroup) {
                set
            } else {
                set.copy(items = set.items.filter { it.matchesAll(keywords) })
            }
        }
    }
    val keywords = keywordsOf(query.globalSearch)
    if (keywords.isEmpty()) return this
    return when (query.mode) {
        ProxySetSearchMode.ProxySet -> filter { it.matchesAll(keywords) }

        ProxySetSearchMode.Outbound -> mapNotNull { set ->
            val matching = set.items.filter { it.matchesAll(keywords) }
            set.copy(items = matching).takeIf { matching.isNotEmpty() }
        }
    }
}

private fun ProxySet.matchesAll(keywords: List<String>) = matchesAll(keywords, tag, displayType)

private fun ProxyItem.matchesAll(keywords: List<String>) = matchesAll(keywords, tag, type, displayType)

private fun matchesAll(keywords: List<String>, vararg texts: String): Boolean {
    val haystacks = texts.map { it.lowercase() }
    return keywords.all { keyword -> haystacks.any { it.contains(keyword) } }
}

private fun keywordsOf(query: String): List<String> {
    if (query.isBlank()) return emptyList()
    return query.trim().split(WHITESPACE).map { it.lowercase() }
}

private val WHITESPACE = Regex("\\s+")
