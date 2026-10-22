package app.criptopanel.alerts

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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Alerts, kept on the device in one JSON file written atomically (same approach as the portfolio). */
class AlertStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _data = MutableStateFlow(AlertsData())
    val data: StateFlow<AlertsData> = _data.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _data.value = withContext(io) {
            if (!file.exists()) return@withContext AlertsData()
            try {
                appJson.decodeFromString<AlertsData>(file.readText())
            } catch (e: SerializationException) {
                file.renameTo(File(file.path + ".bad"))
                AlertsData()
            } catch (e: IllegalArgumentException) {
                file.renameTo(File(file.path + ".bad"))
                AlertsData()
            }
        }
        loaded = true
    }

    private suspend fun change(transform: (List<Alert>) -> List<Alert>) = mutex.withLock {
        ensureLoaded()
        val next = AlertsData(alerts = transform(_data.value.alerts))
        withContext(io) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(appJson.encodeToString(AlertsData.serializer(), next))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        _data.value = next
    }

    suspend fun upsert(alert: Alert) = change { list ->
        if (list.any { it.id == alert.id }) list.map { if (it.id == alert.id) alert else it } else list + alert
    }

    suspend fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    suspend fun setEnabled(id: String, enabled: Boolean) = change { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    /** Saved before the notification is posted, so a crash right after can never make it fire twice. */
    suspend fun markTriggered(id: String, at: Long) =
        change { list -> list.map { if (it.id == id) it.copy(enabled = false, lastTriggered = at) else it } }
}
