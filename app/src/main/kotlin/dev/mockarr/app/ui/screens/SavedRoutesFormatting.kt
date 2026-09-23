package dev.mockarr.app.ui.screens

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a saved-route card reads its name: the title, and the place after the last comma. */
data class RouteTitle(val title: String, val place: String?)

/**
 * Geocoded names arrive as "Portals of the Past to Middle Drive West, San Francisco".
 * The card shows the part before the last comma as the title and the rest as the
 * location line, so titles stop wrapping to three lines.
 */
fun splitRouteName(name: String): RouteTitle {
    val index = name.lastIndexOf(", ")
    if (index <= 0 || index == name.length - 2) return RouteTitle(name.trim(), null)
    return RouteTitle(name.substring(0, index).trim(), name.substring(index + 2).trim())
}

/** Bucket for the "Created …" line; the screen turns it into localised copy. */
sealed interface CreatedWhen {
    data object Today : CreatedWhen
    data object Yesterday : CreatedWhen
    data class OnDate(val formatted: String) : CreatedWhen
}

/**
 * Date patterns for the "Created …" line. The screen passes the device's best
 * patterns (Android's DateFormat skeletons "MMMd" / "yMMMd"), so German reads
 * "10. Sept." and US English "Sep 10"; these English defaults keep tests JVM-only.
 */
data class DatePatterns(val monthDay: String = "MMM d", val monthDayYear: String = "MMM d, yyyy")

fun createdWhen(
    epochMillis: Long,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    patterns: DatePatterns = DatePatterns(),
): CreatedWhen {
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    return when (date) {
        today -> CreatedWhen.Today
        today.minusDays(1) -> CreatedWhen.Yesterday
        else -> CreatedWhen.OnDate(date.format(dateFormatFor(date, today, patterns)))
    }
}

private fun dateFormatFor(date: LocalDate, today: LocalDate, patterns: DatePatterns): DateTimeFormatter =
    if (date.year == today.year) {
        DateTimeFormatter.ofPattern(patterns.monthDay, Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern(patterns.monthDayYear, Locale.getDefault())
    }
