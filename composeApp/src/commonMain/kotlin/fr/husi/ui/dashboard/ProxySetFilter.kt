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
                set.copy(
                    items = set.items.filter {
                        matchesKeywords(it.tag, keywords)
                    },
                )
            }
        }
    }
    val keywords = keywordsOf(query.globalSearch)
    if (keywords.isEmpty()) return this
    return when (query.mode) {
        ProxySetSearchMode.ProxySet -> filter {
            matchesKeywords(it.tag, keywords)
        }

        ProxySetSearchMode.Outbound -> mapNotNull { set ->
            val matching = set.items.filter {
                matchesKeywords(it.tag, keywords)
            }
            set.copy(items = matching).takeIf { matching.isNotEmpty() }
        }
    }
}

fun matchesKeywords(text: String, keywords: List<String>): Boolean {
    if (keywords.isEmpty()) return true
    val haystack = text.lowercase()
    return keywords.all { haystack.contains(it) }
}

fun keywordsOf(query: String): List<String> {
    if (query.isBlank()) return emptyList()
    return query.trim().split(WHITESPACE).map { it.lowercase() }
}

private val WHITESPACE = Regex("\\s+")
