package dev.mockarr.app.ui

/**
 * A reverse-geocoded road name as a person would say it: "Woodall Rodgers Fwy Eb" reads
 * "Woodall Rodgers Freeway". Expands the highway abbreviations map data uses and drops a
 * trailing carriageway direction (NB / SB / EB / WB), which names one side of a divided
 * road, not a place (Ethan, 2026-09-29). Display only: routing and search never see it.
 */
fun tidyRoadName(name: String): String {
    val words = name.trim().split(WHITESPACE).filter { it.isNotEmpty() }.toMutableList()
    if (words.size > 1 && words.last().lowercase() in DIRECTIONS) words.removeAt(words.lastIndex)
    return words.joinToString(" ") { word -> EXPANSIONS[word.lowercase().trimEnd('.')] ?: word }
}

private val WHITESPACE = Regex("\\s+")
private val DIRECTIONS = setOf("nb", "sb", "eb", "wb")
private val EXPANSIONS = mapOf(
    "fwy" to "Freeway",
    "hwy" to "Highway",
    "pkwy" to "Parkway",
    "expy" to "Expressway",
)
