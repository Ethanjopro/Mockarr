package dev.mockarr.core.model

/** How distances are shown to the user. */
enum class DistanceUnits {
    KILOMETERS,
    MILES,
    ;

    companion object {
        // Countries that use miles for road distances.
        private val MILE_COUNTRIES = setOf("US", "GB", "LR", "MM")

        fun defaultForCountry(country: String): DistanceUnits =
            if (country.uppercase() in MILE_COUNTRIES) MILES else KILOMETERS

        fun fromNameOrNull(name: String?): DistanceUnits? = entries.firstOrNull { it.name == name }
    }
}
