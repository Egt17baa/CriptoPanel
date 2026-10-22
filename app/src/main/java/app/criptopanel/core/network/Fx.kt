package app.criptopanel.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate

const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/v1/"

/** ECB reference rates via Frankfurter: free, no key, working days only. */
interface FrankfurterApi {
    @GET("latest")
    suspend fun latest(@Query("base") base: String = "USD", @Query("symbols") symbols: String = "EUR"): FxLatestDto

    /** [range] is "yyyy-MM-dd..yyyy-MM-dd". */
    @GET("{range}")
    suspend fun series(
        @Path("range", encoded = true) range: String,
        @Query("base") base: String = "USD",
        @Query("symbols") symbols: String = "EUR",
    ): FxSeriesDto
}

@Serializable
data class FxLatestDto(val date: String = "", val rates: Map<String, Double> = emptyMap())

@Serializable
data class FxSeriesDto(val rates: Map<String, Map<String, Double>> = emptyMap())

private const val LATEST_TTL_MS = 60 * 60_000L

/** Euros per dollar, today and on past days. */
class FxRepository(private val api: FrankfurterApi, now: () -> Long = System::currentTimeMillis) {
    private val latestCache = TtlCache<Double>(now, LATEST_TTL_MS)
    private val days = sortedMapOf<LocalDate, Double>()

    suspend fun eurPerUsd(force: Boolean = false): Outcome<Double> =
        latestCache.get(force) { checkNotNull(api.latest().rates["EUR"]) { "no EUR rate" } }

    /** Rates for every day in [from]..[to] (weekends take the previous working day); fetched once per missing span. */
    suspend fun eurPerUsdOn(from: LocalDate, to: LocalDate): Outcome<(LocalDate) -> Double> {
        val covered = synchronized(days) { days.isNotEmpty() && !days.firstKey().isAfter(from.minusDays(4)) && !days.lastKey().isBefore(to.minusDays(4)) }
        if (!covered) {
            // A few days earlier so a Monday-to-Sunday span still finds the Friday before it.
            val r = apiCall { api.series("${from.minusDays(7)}..$to") }
            when (r) {
                is Outcome.Ok -> synchronized(days) {
                    r.value.rates.forEach { (d, m) -> m["EUR"]?.let { days[LocalDate.parse(d)] = it } }
                }
                is Outcome.Err -> return r
            }
        }
        val snapshot = synchronized(days) { java.util.TreeMap(days) }
        if (snapshot.isEmpty()) return Outcome.Err(AppError.Unknown(IllegalStateException("no EUR rates")))
        return Outcome.Ok { d -> (snapshot.floorEntry(d) ?: snapshot.firstEntry()).value }
    }
}
