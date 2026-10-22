package app.criptopanel.portfolio

import app.criptopanel.core.network.appJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import app.criptopanel.core.writeTextAtomically

/** Why a change was refused; shown to the user as is. */
const val SHORTFALL_MESSAGE = "No tenías tanta cantidad en esa fecha"

/**
 * The portfolio and favourites, kept only on the device in one JSON file.
 * Every change is validated, then written atomically (temp file + rename), so a crash never leaves half a file.
 */
class PortfolioStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _data = MutableStateFlow(PortfolioData())
    val data: StateFlow<PortfolioData> = _data.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    /** Reads the file once; safe to call many times. A damaged file is kept aside as `.bad` and the app starts empty. */
    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _data.value = withContext(io) {
            if (!file.exists()) return@withContext PortfolioData()
            try {
                appJson.decodeFromString<PortfolioData>(file.readText())
            } catch (e: SerializationException) {
                file.renameTo(File(file.path + ".bad"))
                PortfolioData()
            } catch (e: IllegalArgumentException) {
                file.renameTo(File(file.path + ".bad"))
                PortfolioData()
            }
        }
        loaded = true
    }

    private suspend fun write(next: PortfolioData) {
        withContext(io) {
            file.writeTextAtomically(appJson.encodeToString(PortfolioData.serializer(), next))
        }
        _data.value = next
    }

    /** Applies [change] if every sell stays covered; returns an error message otherwise. */
    private suspend fun commit(change: (PortfolioData) -> PortfolioData): String? = mutex.withLock {
        ensureLoaded()
        val next = change(_data.value)
        if (firstShortfall(next.operations) != null) return@withLock SHORTFALL_MESSAGE
        write(next)
        null
    }

    /** Adds [op], or replaces the operation with the same id. */
    suspend fun upsert(op: Operation): String? {
        val clean = op.copy(note = op.note.replace(Regex("[\\r\\n]+"), " ").trim().take(NOTE_MAX))
        return commit { d ->
            val exists = d.operations.any { it.id == clean.id }
            d.copy(operations = if (exists) d.operations.map { if (it.id == clean.id) clean else it } else d.operations + clean)
        }
    }

    /**
     * Adds imported operations, skipping ids already present (importing the same file twice adds nothing).
     * Returns how many were added, or null if a sell would go below zero (then nothing changes).
     */
    suspend fun addImported(ops: List<Operation>): Int? = mutex.withLock {
        ensureLoaded()
        val current = _data.value
        val ids = current.operations.map { it.id }.toSet()
        val fresh = ops.filter { it.id !in ids }.distinctBy { it.id }
        val next = current.copy(operations = current.operations + fresh)
        if (firstShortfall(next.operations) != null) return@withLock null
        if (fresh.isNotEmpty()) write(next)
        fresh.size
    }

    /** Deleting a buy can uncover a later sell; then nothing changes and the message is returned. */
    suspend fun delete(id: String): String? = commit { d -> d.copy(operations = d.operations.filterNot { it.id == id }) }

    suspend fun toggleFavorite(coinId: String) {
        commit { d -> d.copy(favorites = if (coinId in d.favorites) d.favorites - coinId else d.favorites + coinId) }
    }

    suspend fun exportCsv(): String = mutex.withLock {
        ensureLoaded()
        _data.value.operations.toCsv()
    }

    /** Validates the whole file and the resulting portfolio before changing anything. */
    suspend fun importCsv(text: String): CsvImport = mutex.withLock {
        ensureLoaded()
        val current = _data.value
        when (val parsed = parseCsv(text, current.operations.map { it.id }.toSet())) {
            is CsvImport.Error -> parsed
            is CsvImport.Ok -> {
                val next = current.copy(operations = current.operations + parsed.operations)
                val short = firstShortfall(next.operations)
                if (short != null) {
                    CsvImport.Error(0, "La venta de ${short.symbol} del ${short.date} deja la cantidad en negativo")
                } else {
                    if (parsed.operations.isNotEmpty()) write(next)
                    parsed
                }
            }
        }
    }
}
