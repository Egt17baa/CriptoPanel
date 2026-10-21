package app.criptopanel.search

import app.criptopanel.market.SearchCoinDto

data class SearchCoin(val id: String, val name: String, val symbol: String, val thumbUrl: String?, val rank: Int?)

fun SearchCoinDto.toDomain() = SearchCoin(id, name, symbol.uppercase(), thumb, marketCapRank)
