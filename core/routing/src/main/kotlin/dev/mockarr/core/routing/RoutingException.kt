package dev.mockarr.core.routing

/** Typed routing failures so the UI can react specifically. */
sealed class RoutingException(message: String) : Exception(message) {

    /** The backend could not find a road route between the waypoints. */
    class NoRoute : RoutingException("No route found between these points")

    /** Rate-limited (HTTP 429) — likely the shared demo server. */
    class RateLimited : RoutingException("Routing server is busy — try again in a moment")

    class Server(detail: String) : RoutingException("Routing server error: $detail")

    class Network(cause: Throwable) : RoutingException("Network error: ${cause.message ?: "unreachable"}") {
        init {
            initCause(cause)
        }
    }
}
