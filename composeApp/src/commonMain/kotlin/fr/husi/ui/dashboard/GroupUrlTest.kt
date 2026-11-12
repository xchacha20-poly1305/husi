package fr.husi.ui.dashboard

internal const val GROUP_URL_TEST_CONCURRENCY = 10

internal fun skipGroupUrlTest(item: ProxyItem): Boolean {
    return item.type == "direct" || item.type == "block"
}

internal fun expandUrlTestTargets(
    items: List<ProxyItem>,
    members: Map<String, List<ProxyItem>>,
): List<ProxyItem> {
    val visited = mutableSetOf<String>()
    val leaves = mutableListOf<ProxyItem>()

    fun walk(item: ProxyItem) {
        if (!visited.add(item.tag)) return
        val nested = members[item.tag]
        if (nested == null) {
            if (!skipGroupUrlTest(item)) {
                leaves += item
            }
            return
        }
        for (member in nested) {
            walk(member)
        }
    }

    for (item in items) {
        walk(item)
    }
    return leaves
}
