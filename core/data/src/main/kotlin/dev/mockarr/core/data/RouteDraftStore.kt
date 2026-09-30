package dev.mockarr.core.data

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.Waypoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The route on the map as it stood: its stops, travel mode, the road route drawn for them
 * ([routedFor]: the stop positions it was fetched for) and whether it is saved.
 */
@Serializable
data class RouteDraft(
    val waypoints: List<Waypoint>,
    val profile: RoutingProfile,
    val route: Route? = null,
    val routedFor: List<LatLng>? = null,
    val routeSaved: Boolean = false,
    val routeIsFallback: Boolean = false,
)

/**
 * The one [RouteDraft] on disk, so a route being built survives the app closing: Back from
 * the map finished the Activity and took an unsaved multi-stop route with it, and Android
 * kills a backgrounded app whenever it likes (critique 2026-09-29). Written as the route
 * changes, cleared when the map has no stops, read once when the map starts empty.
 *
 * Same discipline as [SessionSnapshotStore]: one thread, call order, atomic replace.
 */
class RouteDraftStore(
    private val file: File,
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private val temp = File(file.path + ".tmp")

    /** The stored draft, or null when there is none or it is unreadable (a newer format, a torn file). */
    suspend fun read(): RouteDraft? = withContext(dispatcher) {
        try {
            if (file.exists()) json.decodeFromString(RouteDraft.serializer(), file.readText()) else null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    /** Atomic replace: the previous draft stays intact if the write dies halfway. */
    fun write(draft: RouteDraft): Job = scope.launch {
        try {
            file.parentFile?.mkdirs()
            temp.writeText(json.encodeToString(RouteDraft.serializer(), draft))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        } catch (_: IOException) {
            // A failed draft only costs restoring the route after the app closes.
        }
    }

    fun clear(): Job = scope.launch {
        file.delete()
        temp.delete()
    }

    companion object {
        const val FILE_NAME = "route_draft.json"
    }
}
