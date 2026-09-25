package dev.mockarr.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyPlaceTest {

    @Test
    fun `backfills the text after the last comma`() {
        assertEquals("Dallas", legacyPlaceOf("Klyde Warren Park - The East Lawn to Canton, Dallas"))
    }

    @Test
    fun `no comma, a leading comma or nothing after it is no place`() {
        assertNull(legacyPlaceOf("Loop"))
        assertNull(legacyPlaceOf(", Dallas"))
        assertNull(legacyPlaceOf("Route Aug 18, "))
    }
}
