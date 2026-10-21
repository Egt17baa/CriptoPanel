package app.criptopanel.coins

enum class SortKey(val label: String) {
    RANK("Posición"),
    CHANGE_24H("24 h"),
    CHANGE_7D("7 d"),
    VOLUME("Volumen"),
}

private fun descending(selector: (CoinMarket) -> Double?): Comparator<CoinMarket> =
    compareBy(nullsLast(reverseOrder()), selector)

/** Case-insensitive match on name or symbol; rank ascending, everything else descending; nulls last. */
fun filterAndSort(coins: List<CoinMarket>, query: String, sort: SortKey): List<CoinMarket> {
    val q = query.trim()
    val filtered = if (q.isEmpty()) coins else coins.filter {
        it.name.contains(q, ignoreCase = true) || it.symbol.contains(q, ignoreCase = true)
    }
    val comparator = when (sort) {
        SortKey.RANK -> compareBy(nullsLast()) { it.rank }
        SortKey.CHANGE_24H -> descending { it.change24h }
        SortKey.CHANGE_7D -> descending { it.change7d }
        SortKey.VOLUME -> descending { it.volume24h }
    }
    return filtered.sortedWith(comparator)
}
