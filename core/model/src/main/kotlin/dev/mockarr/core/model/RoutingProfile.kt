package dev.mockarr.core.model

/** Travel mode used when generating a route. */
enum class RoutingProfile {
    DRIVING,
    WALKING,
    CYCLING,
    ;

    companion object {
        /** Parses a stored name, falling back to [DRIVING] for unknown values. */
        fun fromNameOrDefault(name: String?): RoutingProfile =
            entries.firstOrNull { it.name == name } ?: DRIVING
    }
}
