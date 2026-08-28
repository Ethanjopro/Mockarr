package dev.mockarr.app.ui.screens

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedRoutesFormattingTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `splits the place after the last comma`() {
        val split = splitRouteName("Portals of the Past to Middle Drive West, San Francisco")

        assertEquals("Portals of the Past to Middle Drive West", split.title)
        assertEquals("San Francisco", split.place)
    }

    @Test
    fun `keeps a name without a comma whole`() {
        val split = splitRouteName("Route Aug 18, ")

        assertEquals("Route Aug 18,", split.title)
        assertNull(split.place)
        assertNull(splitRouteName("Loop").place)
    }

    @Test
    fun `created buckets today and yesterday before dates`() {
        val now = at(2026, 8, 28, hour = 23)

        assertEquals(CreatedWhen.Today, createdWhen(at(2026, 8, 28, hour = 1), now, zone))
        assertEquals(CreatedWhen.Yesterday, createdWhen(at(2026, 8, 27), now, zone))
        val older = createdWhen(at(2026, 8, 18), now, zone)
        assertTrue(older is CreatedWhen.OnDate && older.formatted.endsWith("18"))
    }

    @Test
    fun `dates from another year carry the year`() {
        val older = createdWhen(at(2025, 12, 31), at(2026, 8, 28), zone)

        assertTrue(older is CreatedWhen.OnDate && older.formatted.contains("2025"))
    }
}
