package dev.mockarr.core.data

import dev.mockarr.core.model.SessionSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The one [SessionSnapshot] on disk: written by the playback service while a
 * drive or hold runs, cleared when the session ends on purpose, and read back
 * on the next launch to offer "Resume". A snapshot older than
 * [RESUME_WINDOW_MILLIS] is stale (the wake lock would have lapsed anyway) and
 * reads as absent.
 *
 * Writes and clears run on one thread in call order and on the store's own
 * scope, so a clear issued as the service dies still lands after the last write.
 */
class SessionSnapshotStore(
    private val file: File,
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private val temp = File(file.path + ".tmp")

    /** The stored snapshot, or null when there is none, it is stale, or it is unreadable. */
    suspend fun read(nowMillis: Long): SessionSnapshot? = withContext(dispatcher) {
        val snapshot = decode() ?: return@withContext null
        val fresh = nowMillis - snapshot.savedAtEpochMillis in 0..RESUME_WINDOW_MILLIS
        if (fresh) snapshot else null
    }

    /** True while any snapshot file exists, stale or not — the last session did not end on purpose. */
    suspend fun hasLeftover(): Boolean = withContext(dispatcher) { file.exists() }

    /** Atomic replace: the previous snapshot stays intact if the write dies halfway. */
    fun write(snapshot: SessionSnapshot): Job = scope.launch {
        try {
            file.parentFile?.mkdirs()
            temp.writeText(json.encodeToString(SessionSnapshot.serializer(), snapshot))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        } catch (_: IOException) {
            // A failed snapshot only costs the resume offer; the drive itself is unaffected.
        }
    }

    fun clear(): Job = scope.launch {
        file.delete()
        temp.delete()
    }

    private fun decode(): SessionSnapshot? = try {
        if (file.exists()) json.decodeFromString(SessionSnapshot.serializer(), file.readText()) else null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IOException) {
        null
    }

    companion object {
        const val FILE_NAME = "session_snapshot.json"
        const val RESUME_WINDOW_MILLIS: Long = 6L * 60 * 60 * 1000
    }
}
